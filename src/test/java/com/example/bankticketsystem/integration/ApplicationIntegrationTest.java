package com.example.bankticketsystem.integration;

import com.example.bankticketsystem.dto.ApplicationDto;
import com.example.bankticketsystem.dto.ApplicationHistoryDto;
import com.example.bankticketsystem.dto.DocumentDto;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.ApplicationRepository;
import com.example.bankticketsystem.repository.ProductRepository;
import com.example.bankticketsystem.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApplicationIntegrationTest {

    @Container
    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        reg.add("spring.datasource.username", POSTGRES::getUsername);
        reg.add("spring.datasource.password", POSTGRES::getPassword);
        // if you use Hibernate ddl-auto in tests:
        reg.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @BeforeEach
    public void cleanDb() {
        applicationRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void applicationLifecycle_createGetListAddRemoveTagsChangeStatusHistoryDelete() throws JsonProcessingException {
        // 1) create applicant user
        User applicant = new User();
        UUID applicantId = UUID.randomUUID();
        applicant.setId(applicantId);
        applicant.setUsername("applicant");
        applicant.setEmail("applicant@example.com");
        applicant.setPasswordHash("x"); // not used by controller
        applicant.setRole(UserRole.ROLE_USER);
        applicant.setCreatedAt(Instant.now());
        userRepository.save(applicant);

        // 2) create a product
        Product product = new Product();
        UUID productId = UUID.randomUUID();
        product.setId(productId);
        product.setName("TestProduct");
        product.setDescription("desc");
        productRepository.save(product);

        // 3) create application via controller (POST)
        ApplicationDto createReq = new ApplicationDto();
        createReq.setApplicantId(applicantId);
        createReq.setProductId(productId);
        createReq.setTags(List.of("initial-tag"));
        DocumentDto doc = new DocumentDto();
        doc.setFileName("f.txt");
        doc.setContentType("text/plain");
        doc.setStoragePath("/tmp/f");
        createReq.setDocuments(List.of(doc));

        ResponseEntity<ApplicationDto> createResp = rest.postForEntity("/api/v1/applications", createReq, ApplicationDto.class);
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        ApplicationDto created = createResp.getBody();
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals(applicantId, created.getApplicantId());
        assertEquals(productId, created.getProductId());
        assertTrue(created.getTags() != null && created.getTags().contains("initial-tag"));
        URI location = createResp.getHeaders().getLocation();
        assertNotNull(location);

        UUID appId = created.getId();

        // 4) list should contain the created application
        ResponseEntity<ApplicationDto[]> listResp = rest.getForEntity("/api/v1/applications", ApplicationDto[].class);
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        ApplicationDto[] listBody = listResp.getBody();
        assertNotNull(listBody);
        assertTrue(Arrays.stream(listBody).anyMatch(a -> appId.equals(a.getId())));

        // 5) GET by id should return the application
        ResponseEntity<ApplicationDto> getResp = rest.getForEntity("/api/v1/applications/" + appId, ApplicationDto.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        ApplicationDto got = getResp.getBody();
        assertNotNull(got);
        assertEquals(applicantId, got.getApplicantId());

        // 6) add tags as applicant (PUT /{id}/tags?actorId=...)
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<String>> addTagsEntity = new HttpEntity<>(List.of("extra-tag"), jsonHeaders);

        ResponseEntity<Void> addTagsResp = rest.exchange(
                "/api/v1/applications/" + appId + "/tags?actorId=" + applicantId,
                HttpMethod.PUT,
                addTagsEntity,
                Void.class
        );

        assertEquals(HttpStatus.NO_CONTENT, addTagsResp.getStatusCode());

        // fetch application again and check tags present
        ResponseEntity<ApplicationDto> afterAddGet = rest.getForEntity("/api/v1/applications/" + appId, ApplicationDto.class);
        assertEquals(HttpStatus.OK, afterAddGet.getStatusCode());
        ApplicationDto afterAdd = afterAddGet.getBody();
        assertNotNull(afterAdd);
        assertTrue(afterAdd.getTags().contains("extra-tag"));

        // 7) remove tag as applicant (DELETE /{id}/tags?actorId=...) with body ["initial-tag"]
        HttpEntity<List<String>> removeTagsEntity = new HttpEntity<>(List.of("initial-tag"), jsonHeaders);

        ResponseEntity<Void> removeTagsResp = rest.exchange(
                "/api/v1/applications/" + appId + "/tags?actorId=" + applicantId,
                HttpMethod.DELETE,
                removeTagsEntity,
                Void.class
        );

        assertEquals(HttpStatus.NO_CONTENT, removeTagsResp.getStatusCode());

        // fetch application again and check initial-tag removed
        ResponseEntity<ApplicationDto> afterRemoveGet = rest.getForEntity("/api/v1/applications/" + appId, ApplicationDto.class);
        assertEquals(HttpStatus.OK, afterRemoveGet.getStatusCode());
        ApplicationDto afterRemove = afterRemoveGet.getBody();
        assertNotNull(afterRemove);
        assertFalse(afterRemove.getTags().contains("initial-tag"));
        assertTrue(afterRemove.getTags().contains("extra-tag")); // extra-tag remains

        // 8) create an admin user to change status and delete
        User admin = new User();
        UUID adminId = UUID.randomUUID();
        admin.setId(adminId);
        admin.setUsername("adminActor");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("adminpass");
        admin.setRole(UserRole.ROLE_ADMIN);
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);

        // 9) Change status via PUT /api/v1/applications/{id}/status?actorId=... with JSON-string body "APPROVED"
        // Controller expects @RequestBody String status, so send a JSON string literal (quotes) or plain text.
        HttpHeaders statusHeaders = new HttpHeaders();
        statusHeaders.setContentType(MediaType.TEXT_PLAIN);
        String plainBody = "APPROVED";
        HttpEntity<String> statusEntity = new HttpEntity<>(plainBody, statusHeaders);

        // perform the status change and read raw response as String to avoid RestTemplate mapping issues
        ResponseEntity<String> statusResp = rest.exchange(
                "/api/v1/applications/" + appId + "/status?actorId=" + adminId,
                HttpMethod.PUT,
                statusEntity,
                String.class
        );

        assertEquals(HttpStatus.OK, statusResp.getStatusCode());
        String statusBodyStr = statusResp.getBody();
        assertNotNull(statusBodyStr);

        // parse JSON body into ApplicationDto using Jackson (avoid TestRestTemplate auto-mapping issues)
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        // avoid serializing dates as timestamps
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        ApplicationDto updated = mapper.readValue(statusBodyStr, ApplicationDto.class);

        assertNotNull(updated);
        assertEquals("APPROVED", updated.getStatus().name());;

        // 10) Get history via GET /api/v1/applications/{id}/history?actorId=...
        ResponseEntity<ApplicationHistoryDto[]> historyResp = rest.getForEntity(
                "/api/v1/applications/" + appId + "/history?actorId=" + adminId,
                ApplicationHistoryDto[].class
        );
        assertEquals(HttpStatus.OK, historyResp.getStatusCode());
        ApplicationHistoryDto[] history = historyResp.getBody();
        assertNotNull(history);
        assertTrue(history.length >= 1);
        assertEquals(updated.getId(), history[0].getApplicationId());

        // 11) Delete application as admin
        ResponseEntity<Void> deleteResp = rest.exchange(
                "/api/v1/applications/" + appId + "?actorId=" + adminId,
                HttpMethod.DELETE,
                null,
                Void.class
        );
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());

        // 12) GET after delete should return 404
        ResponseEntity<ApplicationDto> afterDeleteGet = rest.getForEntity("/api/v1/applications/" + appId, ApplicationDto.class);
        assertEquals(HttpStatus.NOT_FOUND, afterDeleteGet.getStatusCode());
    }
}
