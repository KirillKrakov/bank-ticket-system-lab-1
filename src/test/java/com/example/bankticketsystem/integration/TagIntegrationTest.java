//package com.example.bankticketsystem.integration;
//
//import com.example.bankticketsystem.dto.ApplicationCreateRequest;
//import com.example.bankticketsystem.dto.DocumentCreateRequest;
//import com.example.bankticketsystem.dto.ApplicationDto;
//import com.example.bankticketsystem.model.entity.Product;
//import com.example.bankticketsystem.model.entity.User;
//import com.example.bankticketsystem.repository.ProductRepository;
//import com.example.bankticketsystem.repository.UserRepository;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.web.client.TestRestTemplate;
//import org.springframework.http.*;
//
//import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//
//import java.util.List;
//import java.util.UUID;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//@Testcontainers
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//public class TagIntegrationTest {
//
//    @Container
//    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
//            .withDatabaseName("banktickets")
//            .withUsername("postgres")
//            .withPassword("postgres");
//
//    @DynamicPropertySource
//    static void props(DynamicPropertyRegistry r) {
//        r.add("spring.datasource.url", postgres::getJdbcUrl);
//        r.add("spring.datasource.username", postgres::getUsername);
//        r.add("spring.datasource.password", postgres::getPassword);
//    }
//
//    @Autowired private TestRestTemplate rest;
//    @Autowired private UserRepository userRepository;
//    @Autowired private ProductRepository productRepository;
//
//    @Test
//    void attachTags_and_listByTag() {
//        User u = new User(); u.setId(UUID.randomUUID()); u.setUsername("taguser"); u.setEmail("t@example.com"); u.setPasswordHash("noop"); u.setCreatedAt(java.time.Instant.now()); userRepository.save(u);
//        Product p = new Product(); p.setId(UUID.randomUUID()); p.setName("tagProd"); productRepository.save(p);
//
//        ApplicationCreateRequest acr = new ApplicationCreateRequest();
//        acr.setApplicantId(u.getId());
//        acr.setProductId(p.getId());
//        acr.setDocuments(List.of());
//        ResponseEntity<ApplicationDto> createResp = rest.postForEntity("/api/v1/applications", acr, ApplicationDto.class);
//        assertEquals(HttpStatus.CREATED, createResp.getStatusCode());
//        ApplicationDto app = createResp.getBody();
//        assertNotNull(app);
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_JSON);
//        HttpEntity<String> ent = new HttpEntity<>("[\"urgent\",\"vip\"]", headers);
//        ResponseEntity<Void> attachResp = rest.postForEntity("/api/v1/applications/" + app.getId() + "/tags", ent, Void.class);
//        assertEquals(HttpStatus.NO_CONTENT, attachResp.getStatusCode());
//
//        ResponseEntity<ApplicationDto[]> listResp = rest.getForEntity("/api/v1/tags/urgent/applications?page=0&size=10", ApplicationDto[].class);
//        assertEquals(HttpStatus.OK, listResp.getStatusCode());
//        ApplicationDto[] arr = listResp.getBody();
//        assertNotNull(arr);
//        assertTrue(arr.length >= 1);
//        boolean found = false;
//        for (ApplicationDto a : arr) {
//            if (a.getId().equals(app.getId())) {
//                found = true;
//                assertTrue(a.getTags().contains("urgent"));
//            }
//        }
//        assertTrue(found);
//    }
//}
