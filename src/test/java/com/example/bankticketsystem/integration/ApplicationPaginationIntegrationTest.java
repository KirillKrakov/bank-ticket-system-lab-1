package com.example.bankticketsystem.integration;

import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.repository.ApplicationRepository;
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

import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApplicationPaginationIntegrationTest {

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
    private ApplicationRepository applicationRepository;

    @Test
    void pagination_sizeLimitEnforced_andPagingWorks() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername("pguser");
        u.setEmail("pg@example.com");
        u.setPasswordHash("noop");
        u.setCreatedAt(java.time.Instant.now());
        userRepository.save(u);

        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setName("pgprod");
        productRepository.save(p);

        IntStream.range(0, 60).forEach(i -> {
            var app = new com.example.bankticketsystem.model.entity.Application();
            app.setId(UUID.randomUUID());
            app.setApplicant(u);
            app.setProduct(p);
            app.setStatus(com.example.bankticketsystem.model.enums.ApplicationStatus.SUBMITTED);
            app.setCreatedAt(java.time.Instant.now());
            applicationRepository.save(app);
        });

        ResponseEntity<String> bad = rest.getForEntity("/api/v1/applications?page=0&size=100", String.class);
        assertEquals(HttpStatus.BAD_REQUEST, bad.getStatusCode());

        ResponseEntity<com.example.bankticketsystem.dto.ApplicationDto[]> ok = rest.getForEntity("/api/v1/applications?page=0&size=20", com.example.bankticketsystem.dto.ApplicationDto[].class);
        assertEquals(HttpStatus.OK, ok.getStatusCode());
        assertTrue(Objects.requireNonNull(ok.getBody()).length <= 20);
    }
}
