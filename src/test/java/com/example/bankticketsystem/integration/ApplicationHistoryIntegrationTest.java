package com.example.bankticketsystem.integration;

import com.example.bankticketsystem.dto.*;
import com.example.bankticketsystem.model.entity.ApplicationHistory;
import com.example.bankticketsystem.repository.ApplicationHistoryRepository;
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
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApplicationHistoryIntegrationTest {

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

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ApplicationHistoryRepository historyRepository;

    @Test
    void createApplication_createsHistoryEntry() {
        // prepare user+product
        var user = new com.example.bankticketsystem.model.entity.User();
        user.setId(UUID.randomUUID());
        user.setUsername("histuser");
        user.setEmail("hist@example.com");
        user.setPasswordHash("noop");
        user.setCreatedAt(java.time.Instant.now());
        userRepository.save(user);

        var prod = new com.example.bankticketsystem.model.entity.Product();
        prod.setId(UUID.randomUUID());
        prod.setName("histProd");
        productRepository.save(prod);

        ApplicationCreateRequest acr = new ApplicationCreateRequest();
        acr.setApplicantId(user.getId());
        acr.setProductId(prod.getId());

        ResponseEntity<ApplicationDto> resp = rest.postForEntity("/api/v1/applications", acr, ApplicationDto.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        ApplicationDto dto = resp.getBody();
        assertNotNull(dto);

        // query history table for that application
        List<ApplicationHistory> list = historyRepository.findAll();
        boolean found = list.stream().anyMatch(h -> h.getApplication().getId().equals(dto.getId()));
        assertTrue(found, "Expected application_history to contain entry for created application");
    }
}
