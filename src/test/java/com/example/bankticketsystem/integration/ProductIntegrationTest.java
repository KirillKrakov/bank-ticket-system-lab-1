package com.example.bankticketsystem.integration;

import com.example.bankticketsystem.dto.ProductDto;
import com.example.bankticketsystem.dto.ProductRequest;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.net.URI;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ProductIntegrationTest {

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
        // if you use Hibernate ddl-auto in tests:
        reg.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository userRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public com.example.bankticketsystem.service.UserService userService() {
            com.example.bankticketsystem.service.UserService mock =
                    mock(com.example.bankticketsystem.service.UserService.class);
            // Настраиваем мок для возврата пользователя по ID
            when(mock.findById(any(UUID.class))).thenAnswer(invocation -> {
                UUID id = invocation.getArgument(0);
                com.example.bankticketsystem.model.entity.User user =
                        new com.example.bankticketsystem.model.entity.User();
                user.setId(id);
                user.setRole(UserRole.ROLE_ADMIN); // Для тестов всегда возвращаем ADMIN
                return Optional.of(user);
            });
            return mock;
        }

        @Bean
        @Primary
        public com.example.bankticketsystem.service.ApplicationService applicationService() {
            com.example.bankticketsystem.service.ApplicationService mock =
                    mock(com.example.bankticketsystem.service.ApplicationService.class);
            // Настраиваем мок для возврата пустого списка заявок
            when(mock.findByProductId(any(UUID.class))).thenReturn(List.of());
            doNothing().when(mock).delete(any(com.example.bankticketsystem.model.entity.Application.class));
            return mock;
        }

        @Bean
        @Primary
        public com.example.bankticketsystem.service.UserProductAssignmentService userProductAssignmentService() {
            com.example.bankticketsystem.service.UserProductAssignmentService mock =
                    mock(com.example.bankticketsystem.service.UserProductAssignmentService.class);
            // Настраиваем мок для проверки прав доступа
            when(mock.existsByUserIdAndProductIdAndRoleOnProduct(any(), any(), any()))
                    .thenReturn(true); // Для тестов всегда возвращаем true (является владельцем)
            doNothing().when(mock).deleteByProductId(any(UUID.class));
            return mock;
        }

        @Bean
        @Primary
        public com.example.bankticketsystem.service.TagService tagService() {
            return mock(com.example.bankticketsystem.service.TagService.class);
        }
    }

    @BeforeEach
    public void cleanDb() {
        // Очистка пользователей (и связанных сущностей) если нужно
        userRepository.deleteAll();
    }

    @Test
    public void productLifecycle_createListGetUpdateDelete_asAdmin() {
        // 1) create product via controller (POST)
        ProductDto createReq = new ProductDto();
        createReq.setName("MyProduct");
        createReq.setDescription("Initial description");

        ResponseEntity<ProductDto> createResp = rest.postForEntity("/api/v1/products", createReq, ProductDto.class);
        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
        ProductDto created = createResp.getBody();
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("MyProduct", created.getName());
        assertEquals("Initial description", created.getDescription());

        // location header should point to resource
        URI location = createResp.getHeaders().getLocation();
        assertNotNull(location);
        assertTrue(location.toString().contains("/api/v1/products/" + created.getId()));

        UUID productId = created.getId();

        // 2) list should contain the created product
        ResponseEntity<ProductDto[]> listResp = rest.getForEntity("/api/v1/products", ProductDto[].class);
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        ProductDto[] listBody = listResp.getBody();
        assertNotNull(listBody);
        assertTrue(Arrays.stream(listBody).anyMatch(p -> productId.equals(p.getId())));

        // 3) GET by id should return the product
        ResponseEntity<ProductDto> getResp = rest.getForEntity("/api/v1/products/" + productId, ProductDto.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        ProductDto got = getResp.getBody();
        assertNotNull(got);
        assertEquals("MyProduct", got.getName());

        // 4) create an admin user directly in DB (actor) to perform update/delete
        User admin = new User();
        admin.setId(UUID.randomUUID()); // обязательно назначаем id вручную
        admin.setUsername("adminActor");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("adminpass"); // не используется контроллером, но БД требует non-null
        admin.setRole(UserRole.ROLE_ADMIN);
        admin.setCreatedAt(Instant.now());
        User savedAdmin = userRepository.save(admin);
        UUID adminId = savedAdmin.getId();
        assertNotNull(adminId);

        // 5) Update product as admin (PUT /api/v1/products/{id}?actorId=...)
        ProductDto updateReq = new ProductDto();
        updateReq.setName("UpdatedProduct");
        updateReq.setDescription("Updated description");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ProductDto> updateEntity = new HttpEntity<>(updateReq, headers);

        ResponseEntity<ProductDto> updateResp = rest.exchange(
                "/api/v1/products/" + productId + "?actorId=" + adminId,
                HttpMethod.PUT,
                updateEntity,
                ProductDto.class
        );

        assertEquals(HttpStatus.OK, updateResp.getStatusCode());
        ProductDto updated = updateResp.getBody();
        assertNotNull(updated);
        assertEquals("UpdatedProduct", updated.getName());
        assertEquals("Updated description", updated.getDescription());

        // 6) Delete product as admin (DELETE /api/v1/products/{id}?actorId=...)
        ResponseEntity<Void> deleteResp = rest.exchange(
                "/api/v1/products/" + productId + "?actorId=" + adminId,
                HttpMethod.DELETE,
                null,
                Void.class
        );

        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());

        // 7) GET after delete should return 404
        ResponseEntity<ProductDto> afterDeleteGet = rest.getForEntity("/api/v1/products/" + productId, ProductDto.class);
        assertEquals(HttpStatus.NOT_FOUND, afterDeleteGet.getStatusCode());

        // 8) list should no longer contain the deleted product
        ResponseEntity<ProductDto[]> finalListResp = rest.getForEntity("/api/v1/products", ProductDto[].class);
        assertEquals(HttpStatus.OK, finalListResp.getStatusCode());
        ProductDto[] finalListBody = finalListResp.getBody();
        assertNotNull(finalListBody);
        assertFalse(Arrays.stream(finalListBody).anyMatch(p -> productId.equals(p.getId())));
    }
}