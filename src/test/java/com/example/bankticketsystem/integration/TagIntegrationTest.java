package com.example.bankticketsystem.integration;

import com.example.bankticketsystem.dto.ApplicationDto;
import com.example.bankticketsystem.dto.TagDto;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.ProductRepository;
import com.example.bankticketsystem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TagIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("banktickets")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;

    @Test
    void createListAndGetTagWithApplications() {

        // ----- Create User -----
        User admin = new User();
        UUID adminId = UUID.randomUUID();
        admin.setId(adminId);
        admin.setUsername("adminActor");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("adminpass");
        admin.setRole(UserRole.ROLE_ADMIN);
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);

        // ----- Create Product -----
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setName("TagProd");
        productRepository.save(p);

        // ----- Create Application -----
        ApplicationDto acr = new ApplicationDto();
        acr.setApplicantId(admin.getId());
        acr.setProductId(p.getId());
        acr.setDocuments(List.of());

        ResponseEntity<ApplicationDto> createResp =
                rest.postForEntity("/api/v1/applications", acr, ApplicationDto.class);

        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        ApplicationDto app = createResp.getBody();
        assertNotNull(app);
        assertNotNull(app.getId());

        // ----- Attach Tags -----

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<String>> ent = new HttpEntity<>(List.of("urgent", "vip"), headers);

        ResponseEntity<Void> attachResp = rest.exchange(
                "/api/v1/applications/" + app.getId() + "/tags?actorId=" + adminId,
                HttpMethod.PUT,
                ent,
                Void.class
        );

        assertEquals(HttpStatus.NO_CONTENT, attachResp.getStatusCode());

        // ----- Test: Create tag explicitly -----
        ResponseEntity<TagDto> tagResp = rest.postForEntity("/api/v1/tags", "manual", TagDto.class);

        assertEquals(HttpStatus.CREATED, tagResp.getStatusCode());
        assertEquals("manual", Objects.requireNonNull(tagResp.getBody()).getName());
        assertNotNull(tagResp.getBody().getId());

        // ----- Test: List tags -----
        ResponseEntity<TagDto[]> listResp = rest.getForEntity("/api/v1/tags", TagDto[].class);

        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        TagDto[] tags = listResp.getBody();
        assertNotNull(tags);
        assertTrue(tags.length >= 3); // urgent, vip, manual

        // ----- Test: GET /api/v1/tags/urgent/applications -----
        ResponseEntity<TagDto> tagAppsResp =
                rest.getForEntity("/api/v1/tags/urgent/applications", TagDto.class);

        assertEquals(HttpStatus.OK, tagAppsResp.getStatusCode());
        TagDto urgentDto = tagAppsResp.getBody();

        assertNotNull(urgentDto);
        assertEquals("urgent", urgentDto.getName());
        assertNotNull(urgentDto.getApplications());
        assertFalse(urgentDto.getApplications().isEmpty());

        boolean found = urgentDto.getApplications()
                .stream()
                .anyMatch(a -> a.getId().equals(app.getId()));

        assertTrue(found, "Application with tag 'urgent' should be present");
    }
}
