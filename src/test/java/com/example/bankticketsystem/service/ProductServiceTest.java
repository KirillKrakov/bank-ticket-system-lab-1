package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.ProductCreateRequest;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.repository.ApplicationRepository;
import com.example.bankticketsystem.repository.ProductRepository;
import com.example.bankticketsystem.repository.UserProductAssignmentRepository;
import com.example.bankticketsystem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ProductServiceTest {

    @Test
    void createProduct_success() {
        ProductRepository repo = Mockito.mock(ProductRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        UserProductAssignmentRepository assignmentRepository = Mockito.mock(UserProductAssignmentRepository.class);
        ApplicationRepository applicationRepository = Mockito.mock(ApplicationRepository.class);
        Product pSaved = new Product();
        pSaved.setId(UUID.randomUUID());
        pSaved.setName("X");
        pSaved.setDescription("desc");
        when(repo.save(any(Product.class))).thenReturn(pSaved);

        ProductService svc = new ProductService(repo, userRepository, assignmentRepository, applicationRepository);
        ProductCreateRequest req = new ProductCreateRequest();
        req.setName("X");
        req.setDescription("desc");

        var dto = svc.create(req);
        assertNotNull(dto);
        assertEquals("X", dto.getName());
        assertEquals("desc", dto.getDescription());
    }

    @Test
    void listProducts_pagination() {
        ProductRepository repo = Mockito.mock(ProductRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        UserProductAssignmentRepository assignmentRepository = Mockito.mock(UserProductAssignmentRepository.class);
        ApplicationRepository applicationRepository = Mockito.mock(ApplicationRepository.class);
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setName("P1");
        Page<Product> page = new PageImpl<>(List.of(p), PageRequest.of(0,10), 1);
        when(repo.findAll(any(Pageable.class))).thenReturn(page);

        ProductService svc = new ProductService(repo, userRepository, assignmentRepository, applicationRepository);
        var res = svc.list(0,10);
        assertEquals(1, res.getTotalElements());
        assertEquals("P1", res.getContent().get(0).getName());
    }

    @Test
    void getProduct_notFound_returnsNull() {
        ProductRepository repo = Mockito.mock(ProductRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        UserProductAssignmentRepository assignmentRepository = Mockito.mock(UserProductAssignmentRepository.class);
        ApplicationRepository applicationRepository = Mockito.mock(ApplicationRepository.class);
        when(repo.findById(any())).thenReturn(Optional.empty());
        ProductService svc = new ProductService(repo, userRepository, assignmentRepository, applicationRepository);
        assertNull(svc.get(UUID.randomUUID()));
    }
}
