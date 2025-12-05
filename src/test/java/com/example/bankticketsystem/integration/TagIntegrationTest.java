package com.example.bankticketsystem.integration;

import com.example.bankticketsystem.dto.TagDto;
import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.entity.Tag;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.ApplicationStatus;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.ApplicationRepository;
import com.example.bankticketsystem.repository.ProductRepository;
import com.example.bankticketsystem.repository.TagRepository;
import com.example.bankticketsystem.repository.UserRepository;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

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

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public com.example.bankticketsystem.service.UserService userService() {
            return mock(com.example.bankticketsystem.service.UserService.class);
        }

        @Bean
        @Primary
        public com.example.bankticketsystem.service.ProductService productService() {
            return mock(com.example.bankticketsystem.service.ProductService.class);
        }

        @Bean
        @Primary
        public com.example.bankticketsystem.service.UserProductAssignmentService userProductAssignmentService() {
            return mock(com.example.bankticketsystem.service.UserProductAssignmentService.class);
        }
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private TagRepository tagRepository;

    @BeforeEach
    void cleanDb() {
        applicationRepository.deleteAll();
        tagRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createListAndGetTagWithApplications() {
        // ----- Create User -----
        User admin = new User();
        UUID adminId = UUID.randomUUID();
        admin.setId(adminId);
        admin.setUsername("adminActor");
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("$2a$10$someHashForAdmin");
        admin.setRole(UserRole.ROLE_ADMIN);
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);

        // ----- Create Product -----
        Product p = new Product();
        UUID productId = UUID.randomUUID();
        p.setId(productId);
        p.setName("TagProd");
        p.setDescription("Test product for tags");
        productRepository.save(p);

        // ----- Create Application напрямую через репозиторий -----
        Application app = new Application();
        UUID appId = UUID.randomUUID();
        app.setId(appId);
        app.setApplicant(admin);
        app.setProduct(p);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(Instant.now());

        // Создаем теги и добавляем к заявке
        Tag urgentTag = new Tag();
        urgentTag.setId(UUID.randomUUID());
        urgentTag.setName("urgent");

        Tag vipTag = new Tag();
        vipTag.setId(UUID.randomUUID());
        vipTag.setName("vip");

        tagRepository.saveAll(List.of(urgentTag, vipTag));

        app.setTags(new HashSet<>(List.of(urgentTag, vipTag)));
        applicationRepository.save(app);

        // Также связываем теги с заявкой (обратная связь)
        urgentTag.setApplications(new HashSet<>(List.of(app)));
        vipTag.setApplications(new HashSet<>(List.of(app)));
        tagRepository.saveAll(List.of(urgentTag, vipTag));

        // ----- Test 1: Создаем тег через API -----
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Тег уже существует, поэтому должен вернуть существующий
        HttpEntity<String> createTagEntity = new HttpEntity<>("manual", headers);
        ResponseEntity<TagDto> tagResp = rest.postForEntity(
                "/api/v1/tags",
                createTagEntity,
                TagDto.class
        );

        assertEquals(HttpStatus.CREATED, tagResp.getStatusCode());
        assertEquals("manual", Objects.requireNonNull(tagResp.getBody()).getName());

        // ----- Test 2: List tags -----
        ResponseEntity<TagDto[]> listResp = rest.getForEntity("/api/v1/tags", TagDto[].class);

        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        TagDto[] tags = listResp.getBody();
        assertNotNull(tags);

        // Должно быть как минимум 3 тега: urgent, vip, manual
        assertTrue(tags.length >= 3);

        // Проверяем, что все три тега есть
        boolean hasUrgent = false;
        boolean hasVip = false;
        boolean hasManual = false;
        for (TagDto tag : tags) {
            if ("urgent".equals(tag.getName())) {
                hasUrgent = true;
            }
            if ("vip".equals(tag.getName())) {
                hasVip = true;
            }
            if ("manual".equals(tag.getName())) {
                hasManual = true;
            }
        }
        assertTrue(hasUrgent, "Tag 'urgent' should exist");
        assertTrue(hasVip, "Tag 'vip' should exist");
        assertTrue(hasManual, "Tag 'manual' should exist");

        // ----- Test 3: GET /api/v1/tags/urgent/applications -----
        ResponseEntity<TagDto> tagAppsResp = rest.getForEntity(
                "/api/v1/tags/urgent/applications",
                TagDto.class
        );

        assertEquals(HttpStatus.OK, tagAppsResp.getStatusCode());
        TagDto urgentDto = tagAppsResp.getBody();

        assertNotNull(urgentDto);
        assertEquals("urgent", urgentDto.getName());
        assertNotNull(urgentDto.getApplications());

        // Должна быть как минимум одна заявка с тегом 'urgent'
        assertFalse(urgentDto.getApplications().isEmpty());

        // Проверяем, что наша заявка есть в списке
        boolean found = urgentDto.getApplications()
                .stream()
                .anyMatch(a -> a.getId().equals(app.getId()));

        assertTrue(found, "Application with tag 'urgent' should be present");

        // ----- Test 4: GET /api/v1/tags/vip/applications -----
        ResponseEntity<TagDto> vipTagResp = rest.getForEntity(
                "/api/v1/tags/vip/applications",
                TagDto.class
        );

        assertEquals(HttpStatus.OK, vipTagResp.getStatusCode());
        TagDto vipDto = vipTagResp.getBody();
        assertNotNull(vipDto);
        assertEquals("vip", vipDto.getName());
        assertNotNull(vipDto.getApplications());

        // Проверяем, что заявка есть и в теге 'vip'
        boolean foundInVip = vipDto.getApplications()
                .stream()
                .anyMatch(a -> a.getId().equals(app.getId()));
        assertTrue(foundInVip, "Application with tag 'vip' should be present");

        // ----- Test 5: Проверяем пагинацию -----
        ResponseEntity<TagDto[]> paginatedResp = rest.getForEntity(
                "/api/v1/tags?page=0&size=2",
                TagDto[].class
        );
        assertEquals(HttpStatus.OK, paginatedResp.getStatusCode());
        TagDto[] paginatedTags = paginatedResp.getBody();
        assertNotNull(paginatedTags);
        assertEquals(2, paginatedTags.length);

        // Проверяем заголовок X-Total-Count
        HttpHeaders listHeaders = listResp.getHeaders();
        assertTrue(listHeaders.containsKey("X-Total-Count"));
        String totalCount = listHeaders.getFirst("X-Total-Count");
        assertNotNull(totalCount);
        int count = Integer.parseInt(totalCount);
        assertTrue(count >= 3);
    }
}