package com.example.bankticketsystem.integration;

import com.example.bankticketsystem.dto.AssignmentCreateRequest;
import com.example.bankticketsystem.dto.AssignmentDto;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.AssignmentRole;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AssignmentIntegrationTest {

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
    void createAssignment_and_queryByUser() {
        User u = new User(); u.setId(UUID.randomUUID()); u.setUsername("assuser"); u.setEmail("ass@example.com"); u.setPasswordHash("noop"); u.setCreatedAt(java.time.Instant.now()); userRepository.save(u);
        Product p = new Product(); p.setId(UUID.randomUUID()); p.setName("assprod"); productRepository.save(p);

        AssignmentCreateRequest req = new AssignmentCreateRequest();
        req.setUserId(u.getId());
        req.setProductId(p.getId());
        req.setRole(AssignmentRole.RESELLER);

        ResponseEntity<AssignmentDto> create = rest.postForEntity("/api/v1/assignments", req, AssignmentDto.class);
        assertEquals(HttpStatus.CREATED, create.getStatusCode());
        AssignmentDto dto = create.getBody();
        assertNotNull(dto);
        assertEquals(AssignmentRole.RESELLER, dto.getRole());

        ResponseEntity<AssignmentDto[]> list = rest.getForEntity("/api/v1/assignments?userId=" + u.getId(), AssignmentDto[].class);
        assertEquals(HttpStatus.OK, list.getStatusCode());
        AssignmentDto[] arr = list.getBody();
        assertTrue(arr != null && arr.length >= 1);
    }
}
