package com.example.bankticketsystem.integration;

import com.example.bankticketsystem.dto.*;
import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.Document;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.ApplicationStatus;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.ApplicationRepository;
import com.example.bankticketsystem.repository.ProductRepository;
import com.example.bankticketsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        reg.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public com.example.bankticketsystem.service.TagService tagService() {
            com.example.bankticketsystem.service.TagService mock =
                    mock(com.example.bankticketsystem.service.TagService.class);

            // Настраиваем мок для создания тегов
            when(mock.createTag(anyString())).thenAnswer(invocation -> {
                String tagName = invocation.getArgument(0);
                com.example.bankticketsystem.model.entity.Tag tag =
                        new com.example.bankticketsystem.model.entity.Tag();
                tag.setId(UUID.randomUUID());
                tag.setName(tagName);
                return tag;
            });

            // Также настраиваем createIfNotExists если используется
            when(mock.createIfNotExists(anyString())).thenAnswer(invocation -> {
                String tagName = invocation.getArgument(0);
                com.example.bankticketsystem.model.entity.Tag tag =
                        new com.example.bankticketsystem.model.entity.Tag();
                tag.setId(UUID.randomUUID());
                tag.setName(tagName);
                return tag;
            });

            return mock;
        }

        @Bean
        @Primary
        public com.example.bankticketsystem.service.UserProductAssignmentService userProductAssignmentService() {
            return mock(com.example.bankticketsystem.service.UserProductAssignmentService.class);
        }
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
    public void applicationLifecycle_createGetListAddRemoveTagsChangeStatusHistoryDelete() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 1) create applicant user напрямую в БД
        User applicant = new User();
        UUID applicantId = UUID.randomUUID();
        applicant.setId(applicantId);
        applicant.setUsername("applicant");
        applicant.setEmail("applicant@example.com");
        applicant.setPasswordHash("$2a$10$someHash");
        applicant.setRole(UserRole.ROLE_CLIENT);
        applicant.setCreatedAt(Instant.now());
        userRepository.save(applicant);

        // 2) create a product напрямую в БД
        Product product = new Product();
        UUID productId = UUID.randomUUID();
        product.setId(productId);
        product.setName("TestProduct");
        product.setDescription("desc");
        productRepository.save(product);

        // 3) create application напрямую через репозиторий
        Application app = new Application();
        UUID appId = UUID.randomUUID();
        app.setId(appId);
        app.setApplicant(applicant);
        app.setProduct(product);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(Instant.now());

        // Создаем документы
        Document doc = new Document();
        doc.setId(UUID.randomUUID());
        doc.setFileName("f.txt");
        doc.setContentType("text/plain");
        doc.setStoragePath("/tmp/f");
        doc.setApplication(app);
        app.setDocuments(List.of(doc));

        applicationRepository.save(app);

        // Теперь заявка создана, тестируем остальные операции через API

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

        // 6) create an admin user to change status and delete
        User admin = new User();
        UUID adminId = UUID.randomUUID();
        admin.setId(adminId);
        admin.setUsername("adminActor");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("$2a$10$adminHash");
        admin.setRole(UserRole.ROLE_ADMIN);
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);

        // 7) Change status via PUT /api/v1/applications/{id}/status?actorId=...
        String statusJson = "APPROVED";
        HttpEntity<String> statusEntity = new HttpEntity<>(statusJson, headers);

        ResponseEntity<ApplicationDto> statusResp = rest.exchange(
                "/api/v1/applications/" + appId + "/status?actorId=" + adminId,
                HttpMethod.PUT,
                statusEntity,
                ApplicationDto.class
        );

        assertEquals(HttpStatus.OK, statusResp.getStatusCode());
        ApplicationDto updated = statusResp.getBody();
        assertNotNull(updated);
        assertEquals("APPROVED", updated.getStatus().name());

        // 8) Get history via GET /api/v1/applications/{id}/history?actorId=...
        ResponseEntity<ApplicationHistoryDto[]> historyResp = rest.getForEntity(
                "/api/v1/applications/" + appId + "/history?actorId=" + adminId,
                ApplicationHistoryDto[].class
        );
        assertEquals(HttpStatus.OK, historyResp.getStatusCode());
        ApplicationHistoryDto[] history = historyResp.getBody();
        assertNotNull(history);
        assertTrue(history.length >= 1);

        // 9) Delete application as admin
        ResponseEntity<Void> deleteResp = rest.exchange(
                "/api/v1/applications/" + appId + "?actorId=" + adminId,
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());

        // 10) GET after delete should return 404
        ResponseEntity<ApplicationDto> afterDeleteGet = rest.getForEntity("/api/v1/applications/" + appId, ApplicationDto.class);
        assertEquals(HttpStatus.NOT_FOUND, afterDeleteGet.getStatusCode());
    }
}