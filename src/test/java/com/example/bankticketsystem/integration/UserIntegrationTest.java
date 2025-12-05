package com.example.bankticketsystem.integration;

import com.example.bankticketsystem.dto.UserDto;
import com.example.bankticketsystem.dto.UserRequest;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public com.example.bankticketsystem.service.ApplicationService applicationService() {
            com.example.bankticketsystem.service.ApplicationService mock =
                    mock(com.example.bankticketsystem.service.ApplicationService.class);
            // Настраиваем мок для успешного удаления пользователя
            when(mock.findByApplicantId(any())).thenReturn(List.of());
            doNothing().when(mock).delete(any());
            return mock;
        }

        @Bean
        @Primary
        public com.example.bankticketsystem.service.ProductService productService() {
            return mock(com.example.bankticketsystem.service.ProductService.class);
        }

        @Bean
        @Primary
        public com.example.bankticketsystem.service.TagService tagService() {
            return mock(com.example.bankticketsystem.service.TagService.class);
        }

        @Bean
        @Primary
        public com.example.bankticketsystem.service.UserProductAssignmentService userProductAssignmentService() {
            return mock(com.example.bankticketsystem.service.UserProductAssignmentService.class);
        }
    }

    @BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
    }

    @Test
    public void fullLifecycle_withAdminActor() {
        // 1) create a normal user via POST
        UserRequest createReq = new UserRequest();
        createReq.setUsername("targetUser");
        createReq.setEmail("target@example.com");
        createReq.setPassword("targetPass123");

        ResponseEntity<UserDto> createResp = rest.postForEntity("/api/v1/users", createReq, UserDto.class);
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        assertNotNull(createResp.getBody());
        UUID targetId = createResp.getBody().getId();

        // verify GET /{id}
        ResponseEntity<UserDto> getResp = rest.getForEntity("/api/v1/users/" + targetId, UserDto.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertNotNull(getResp.getBody());
        assertEquals("targetUser", getResp.getBody().getUsername());

        // verify list contains the created user
        ResponseEntity<UserDto[]> listResp = rest.getForEntity("/api/v1/users", UserDto[].class);
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        assertNotNull(listResp.getBody());
        List<UserDto> users = Arrays.asList(listResp.getBody());
        assertTrue(users.stream().anyMatch(u -> targetId.equals(u.getId())));

        // 2) create an admin actor directly in DB (assign id and required fields)
        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setUsername("adminActor");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("$2a$10$someHashForAdmin"); // bcrypt hash for testing
        admin.setRole(UserRole.ROLE_ADMIN);
        admin.setCreatedAt(Instant.now());
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

        ResponseEntity<UserDto> updateResp = rest.exchange(
                "/api/v1/users/" + targetId + "?actorId=" + adminId,
                HttpMethod.PUT,
                updateEntity,
                UserDto.class
        );

        assertEquals(HttpStatus.OK, updateResp.getStatusCode());
        assertNotNull(updateResp.getBody());
        assertEquals("updatedUser", updateResp.getBody().getUsername());
        assertEquals("updated@example.com", updateResp.getBody().getEmail());
        assertEquals(UserRole.ROLE_CLIENT, updateResp.getBody().getRole());

        // 4) promote to manager
        ResponseEntity<Void> promoteResp = rest.exchange(
                "/api/v1/users/" + targetId + "/promote-manager?actorId=" + adminId,
                HttpMethod.PUT,
                HttpEntity.EMPTY,
                Void.class
        );
        assertEquals(HttpStatus.NO_CONTENT, promoteResp.getStatusCode());

        // verify role is MANAGER via GET
        ResponseEntity<UserDto> afterPromoteGet = rest.getForEntity("/api/v1/users/" + targetId, UserDto.class);
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

        ResponseEntity<UserDto> afterDemoteGet = rest.getForEntity("/api/v1/users/" + targetId, UserDto.class);
        assertEquals(HttpStatus.OK, afterDemoteGet.getStatusCode());
        assertEquals(UserRole.ROLE_CLIENT, Objects.requireNonNull(afterDemoteGet.getBody()).getRole());

        // 6) delete the user
        ResponseEntity<Void> deleteResp = rest.exchange(
                "/api/v1/users/" + targetId + "?actorId=" + adminId,
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());

        // GET now should return 404
        ResponseEntity<UserDto> afterDeleteGet = rest.getForEntity("/api/v1/users/" + targetId, UserDto.class);
        assertEquals(HttpStatus.NOT_FOUND, afterDeleteGet.getStatusCode());

        // list should no longer contain the deleted user
        ResponseEntity<UserDto[]> finalListResp = rest.getForEntity("/api/v1/users", UserDto[].class);
        assertEquals(HttpStatus.OK, finalListResp.getStatusCode());
        List<UserDto> finalUsers = Arrays.asList(Objects.requireNonNull(finalListResp.getBody()));
        assertFalse(finalUsers.stream().anyMatch(u -> targetId.equals(u.getId())));
    }
}