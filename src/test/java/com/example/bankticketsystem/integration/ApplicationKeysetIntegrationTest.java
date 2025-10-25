package com.example.bankticketsystem.integration;

import com.example.bankticketsystem.dto.ApplicationDto;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.repository.ApplicationRepository;
import com.example.bankticketsystem.repository.ProductRepository;
import com.example.bankticketsystem.repository.UserRepository;
import com.example.bankticketsystem.util.CursorUtil;
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

import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApplicationKeysetIntegrationTest {

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
    @Autowired private ApplicationRepository applicationRepository;

    @Test
    void keysetPagination_returnsPages_and_noTotalHeader() {
        User u = new User(); u.setId(UUID.randomUUID()); u.setUsername("kuser"); u.setEmail("kuser@example.com"); u.setPasswordHash("noop"); u.setCreatedAt(Instant.now()); userRepository.save(u);
        Product p = new Product(); p.setId(UUID.randomUUID()); p.setName("kprod"); productRepository.save(p);

        IntStream.range(0, 55).forEach(i -> {
            var app = new com.example.bankticketsystem.model.entity.Application();
            app.setId(UUID.randomUUID());
            app.setApplicant(u);
            app.setProduct(p);
            app.setStatus(com.example.bankticketsystem.model.enums.ApplicationStatus.SUBMITTED);
            app.setCreatedAt(Instant.now().minusSeconds(i)); // descending timestamps
            applicationRepository.save(app);
        });

        ResponseEntity<ApplicationDto[]> r1 = rest.getForEntity("/api/v1/applications/stream?limit=20", ApplicationDto[].class);
        assertEquals(HttpStatus.OK, r1.getStatusCode());
        ApplicationDto[] page1 = r1.getBody();
        assertNotNull(page1);
        assertEquals(20, page1.length);

        assertFalse(r1.getHeaders().containsKey("X-Total-Count"));

        ApplicationDto last = page1[page1.length - 1];
        String cursor = CursorUtil.encode(last.getCreatedAt(), last.getId());

        ResponseEntity<ApplicationDto[]> r2 = rest.getForEntity("/api/v1/applications/stream?cursor=" + cursor + "&limit=20", ApplicationDto[].class);
        assertEquals(HttpStatus.OK, r2.getStatusCode());
        ApplicationDto[] page2 = r2.getBody();
        assertNotNull(page2);
        Set<UUID> idsPage1 = new HashSet<>();
        for (ApplicationDto a : page1) idsPage1.add(a.getId());
        for (ApplicationDto a : page2) assertFalse(idsPage1.contains(a.getId()));
    }
}
