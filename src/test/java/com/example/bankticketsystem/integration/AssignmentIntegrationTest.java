package com.example.bankticketsystem.integration;

import com.example.bankticketsystem.dto.UserDto;
import com.example.bankticketsystem.dto.UserProductAssignmentDto;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.AssignmentRole;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.ProductRepository;
import com.example.bankticketsystem.repository.UserProductAssignmentRepository;
import com.example.bankticketsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
    @Autowired private UserProductAssignmentRepository assignmentRepository;

    @BeforeEach
    void cleanDb() {
        assignmentRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void fullLifecycle_withAdminActor() {
        // 1) create an admin actor directly in DB
        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setUsername("adminActor");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("adminpass"); // not used by controller but DB requires non-null
        admin.setRole(UserRole.ROLE_ADMIN);
        admin.setCreatedAt(Instant.now());
        User savedAdmin = userRepository.save(admin);
        UUID adminId = savedAdmin.getId();
        assertNotNull(adminId);

        // 2) create a normal user via POST /api/v1/users
        UserDto createUserReq = new UserDto();
        createUserReq.setUsername("targetUser");
        createUserReq.setEmail("target@example.com");
        createUserReq.setPassword("targetPass123");

        ResponseEntity<UserDto> createUserResp = rest.postForEntity("/api/v1/users", createUserReq, UserDto.class);
        assertEquals(HttpStatus.CREATED, createUserResp.getStatusCode());
        assertNotNull(createUserResp.getBody());
        UUID userId = createUserResp.getBody().getId();

        // 3) create a product directly in DB (assuming no API endpoint for products)
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("testProduct");
        productRepository.save(product);
        UUID productId = product.getId();

        // 4) create assignment via POST /api/v1/assignments?actorId=...
        UserProductAssignmentDto assignReq = new UserProductAssignmentDto();
        assignReq.setUserId(userId);
        assignReq.setProductId(productId);
        assignReq.setRole(AssignmentRole.RESELLER);

        ResponseEntity<UserProductAssignmentDto> assignResp = rest.postForEntity(
                "/api/v1/assignments?actorId=" + adminId,
                assignReq,
                UserProductAssignmentDto.class
        );
        assertEquals(HttpStatus.CREATED, assignResp.getStatusCode());
        assertNotNull(assignResp.getBody());
        UUID assignmentId = assignResp.getBody().getId();
        assertEquals(AssignmentRole.RESELLER, assignResp.getBody().getRole());
        Instant oldAssignedAt = assignResp.getBody().getAssignedAt();

        // verify list by userId contains the assignment
        ResponseEntity<UserProductAssignmentDto[]> listByUserResp = rest.getForEntity(
                "/api/v1/assignments?userId=" + userId,
                UserProductAssignmentDto[].class
        );
        assertEquals(HttpStatus.OK, listByUserResp.getStatusCode());
        assertNotNull(listByUserResp.getBody());
        List<UserProductAssignmentDto> assignmentsByUser = Arrays.asList(listByUserResp.getBody());
        assertEquals(1, assignmentsByUser.size());
        assertEquals(assignmentId, assignmentsByUser.get(0).getId());

        // verify list by productId contains the assignment
        ResponseEntity<UserProductAssignmentDto[]> listByProductResp = rest.getForEntity(
                "/api/v1/assignments?productId=" + productId,
                UserProductAssignmentDto[].class
        );
        assertEquals(HttpStatus.OK, listByProductResp.getStatusCode());
        assertNotNull(listByProductResp.getBody());
        List<UserProductAssignmentDto> assignmentsByProduct = Arrays.asList(listByProductResp.getBody());
        assertEquals(1, assignmentsByProduct.size());
        assertEquals(assignmentId, assignmentsByProduct.get(0).getId());

        // verify full list contains the assignment
        ResponseEntity<UserProductAssignmentDto[]> fullListResp = rest.getForEntity(
                "/api/v1/assignments",
                UserProductAssignmentDto[].class
        );
        assertEquals(HttpStatus.OK, fullListResp.getStatusCode());
        assertNotNull(fullListResp.getBody());
        List<UserProductAssignmentDto> fullAssignments = Arrays.asList(fullListResp.getBody());
        assertTrue(fullAssignments.stream().anyMatch(a -> assignmentId.equals(a.getId())));

        // 5) update assignment by re-assigning with new role
        UserProductAssignmentDto updateReq = new UserProductAssignmentDto();
        updateReq.setUserId(userId);
        updateReq.setProductId(productId);
        updateReq.setRole(AssignmentRole.PRODUCT_OWNER);

        ResponseEntity<UserProductAssignmentDto> updateResp = rest.postForEntity(
                "/api/v1/assignments?actorId=" + adminId,
                updateReq,
                UserProductAssignmentDto.class
        );
        assertEquals(HttpStatus.CREATED, updateResp.getStatusCode());
        assertNotNull(updateResp.getBody());
        assertEquals(assignmentId, updateResp.getBody().getId()); // same ID
        assertEquals(AssignmentRole.PRODUCT_OWNER, updateResp.getBody().getRole());
        assertTrue(updateResp.getBody().getAssignedAt().isAfter(oldAssignedAt));

        // verify updated in list by userId
        ResponseEntity<UserProductAssignmentDto[]> afterUpdateListByUser = rest.getForEntity(
                "/api/v1/assignments?userId=" + userId,
                UserProductAssignmentDto[].class
        );
        assertEquals(HttpStatus.OK, afterUpdateListByUser.getStatusCode());
        List<UserProductAssignmentDto> updatedAssignments = Arrays.asList(Objects.requireNonNull(afterUpdateListByUser.getBody()));
        assertEquals(1, updatedAssignments.size());
        assertEquals(AssignmentRole.PRODUCT_OWNER, updatedAssignments.get(0).getRole());

        // 6) delete the specific assignment
        ResponseEntity<Void> deleteResp = rest.exchange(
                "/api/v1/assignments?actorId=" + adminId + "&userId=" + userId + "&productId=" + productId,
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());

        // verify lists no longer contain the assignment
        ResponseEntity<UserProductAssignmentDto[]> afterDeleteListByUser = rest.getForEntity(
                "/api/v1/assignments?userId=" + userId,
                UserProductAssignmentDto[].class
        );
        assertEquals(HttpStatus.OK, afterDeleteListByUser.getStatusCode());
        assertEquals(0, Objects.requireNonNull(afterDeleteListByUser.getBody()).length);

        ResponseEntity<UserProductAssignmentDto[]> afterDeleteFullList = rest.getForEntity(
                "/api/v1/assignments",
                UserProductAssignmentDto[].class
        );
        assertEquals(HttpStatus.OK, afterDeleteFullList.getStatusCode());
        assertEquals(0, Objects.requireNonNull(afterDeleteFullList.getBody()).length);
    }
}
