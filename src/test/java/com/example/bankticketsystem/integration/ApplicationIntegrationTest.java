package com.example.bankticketsystem.integration;

import com.example.bankticketsystem.dto.*;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.repository.UserRepository;
import com.example.bankticketsystem.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApplicationIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("banktickets")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void createApplicationWithDocument_success() {
        // create user directly in repo
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername("intuser");
        u.setEmail("intuser@example.com");
        u.setPasswordHash("noop");
        u.setCreatedAt(java.time.Instant.now());
        userRepository.save(u);

        // create product via repo
        var p = new com.example.bankticketsystem.model.entity.Product();
        p.setId(UUID.randomUUID());
        p.setName("TestProduct");
        p.setDescription("desc");
        productRepository.save(p);

        ApplicationCreateRequest acr = new ApplicationCreateRequest();
        acr.setApplicantId(u.getId());
        acr.setProductId(p.getId());
        acr.setComment("Please process");
        DocumentCreateRequest dreq = new DocumentCreateRequest();
        dreq.setFileName("doc1.pdf");
        dreq.setContentType("application/pdf");
        acr.setDocuments(List.of(dreq));

        ResponseEntity<ApplicationDto> resp = rest.postForEntity("/api/v1/applications", acr, ApplicationDto.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        ApplicationDto body = resp.getBody();
        assertNotNull(body);
        assertNotNull(body.getId());
        assertEquals(p.getId(), body.getProductId());
        assertEquals(1, body.getDocuments().size());
    }
}
