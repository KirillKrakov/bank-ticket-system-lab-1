package com.example.bankticketsystem.integration;

import com.example.bankticketsystem.dto.request.UserRequest;
import com.example.bankticketsystem.dto.response.UserResponse;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
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
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserIntegrationTest {

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

    @BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
    }

    @Test
    public void createUser_endpoint_returnsCreated() {
        UserRequest req = new UserRequest();
        req.setUsername("integrationUser");
        req.setEmail("int@example.com");
        req.setPassword("password123");

        ResponseEntity<UserResponse> resp = rest.postForEntity("/api/v1/users", req, UserResponse.class);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertNotNull(resp.getBody().getId());
        assertEquals("integrationUser", resp.getBody().getUsername());
        assertEquals("int@example.com", resp.getBody().getEmail());
        assertNotNull(resp.getBody().getCreatedAt());
    }

    @Test
    public void fullLifecycle_withAdminActor() {
        // 1) create a normal user via POST
        UserRequest createReq = new UserRequest();
        createReq.setUsername("targetUser");
        createReq.setEmail("target@example.com");
        createReq.setPassword("targetPass123");

        ResponseEntity<UserResponse> createResp = rest.postForEntity("/api/v1/users", createReq, UserResponse.class);
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        assertNotNull(createResp.getBody());
        UUID targetId = createResp.getBody().getId();

        // verify GET /{id}
        ResponseEntity<UserResponse> getResp = rest.getForEntity("/api/v1/users/" + targetId, UserResponse.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertNotNull(getResp.getBody());
        assertEquals("targetUser", getResp.getBody().getUsername());

        // verify list contains the created user
        ResponseEntity<UserResponse[]> listResp = rest.getForEntity("/api/v1/users", UserResponse[].class);
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        assertNotNull(listResp.getBody());
        List<UserResponse> users = Arrays.asList(listResp.getBody());
        assertTrue(users.stream().anyMatch(u -> targetId.equals(u.getId())));

        // 2) create an admin actor directly in DB (assign id and required fields)
        User admin = new User();
        admin.setId(UUID.randomUUID()); // <-- обязательно назначаем id вручную
        admin.setUsername("adminActor");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("adminpass"); // not used by controller but DB requires non-null
        admin.setRole(UserRole.ROLE_ADMIN);
        admin.setCreatedAt(Instant.now()); // обязательное поле
        User savedAdmin = userRepository.save(admin);
        UUID adminId = savedAdmin.getId();
        assertNotNull(adminId);

        // 3) update the target user using admin actor (PUT /api/v1/users/{id}?actorId=...)
        UserRequest updateReq = new UserRequest();
        updateReq.setUsername("updatedUser");
        updateReq.setEmail("updated@example.com");
        updateReq.setPassword("newpass123");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UserRequest> updateEntity = new HttpEntity<>(updateReq, headers);

        ResponseEntity<UserResponse> updateResp = rest.exchange(
                "/api/v1/users/" + targetId + "?actorId=" + adminId,
                HttpMethod.PUT,
                updateEntity,
                UserResponse.class
        );

        assertEquals(HttpStatus.OK, updateResp.getStatusCode());
        assertNotNull(updateResp.getBody());
        assertEquals("updatedUser", updateResp.getBody().getUsername());
        assertEquals("updated@example.com", updateResp.getBody().getEmail());
        // role remains USER after update
        assertEquals(UserRole.ROLE_USER, updateResp.getBody().getRole());

        // 4) promote to manager
        ResponseEntity<Void> promoteResp = rest.exchange(
                "/api/v1/users/" + targetId + "/promote-manager?actorId=" + adminId,
                HttpMethod.PUT,
                HttpEntity.EMPTY,
                Void.class
        );
        assertEquals(HttpStatus.NO_CONTENT, promoteResp.getStatusCode());

        // verify role is MANAGER via GET
        ResponseEntity<UserResponse> afterPromoteGet = rest.getForEntity("/api/v1/users/" + targetId, UserResponse.class);
        assertEquals(HttpStatus.OK, afterPromoteGet.getStatusCode());
        assertEquals(UserRole.ROLE_MANAGER, Objects.requireNonNull(afterPromoteGet.getBody()).getRole());

        // 5) demote back to user
        ResponseEntity<Void> demoteResp = rest.exchange(
                "/api/v1/users/" + targetId + "/demote-manager?actorId=" + adminId,
                HttpMethod.PUT,
                HttpEntity.EMPTY,
                Void.class
        );
        assertEquals(HttpStatus.NO_CONTENT, demoteResp.getStatusCode());

        ResponseEntity<UserResponse> afterDemoteGet = rest.getForEntity("/api/v1/users/" + targetId, UserResponse.class);
        assertEquals(HttpStatus.OK, afterDemoteGet.getStatusCode());
        assertEquals(UserRole.ROLE_USER, Objects.requireNonNull(afterDemoteGet.getBody()).getRole());

        // 6) delete the user
        ResponseEntity<Void> deleteResp = rest.exchange(
                "/api/v1/users/" + targetId + "?actorId=" + adminId,
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());

        // GET now should return 404
        ResponseEntity<UserResponse> afterDeleteGet = rest.getForEntity("/api/v1/users/" + targetId, UserResponse.class);
        assertEquals(HttpStatus.NOT_FOUND, afterDeleteGet.getStatusCode());

        // list should no longer contain the deleted user
        ResponseEntity<UserResponse[]> finalListResp = rest.getForEntity("/api/v1/users", UserResponse[].class);
        assertEquals(HttpStatus.OK, finalListResp.getStatusCode());
        List<UserResponse> finalUsers = Arrays.asList(Objects.requireNonNull(finalListResp.getBody()));
        assertFalse(finalUsers.stream().anyMatch(u -> targetId.equals(u.getId())));
    }
}