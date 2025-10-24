package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.ProductCreateRequest;
import com.example.bankticketsystem.exception.ConflictException;
import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.AssignmentRole;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.ApplicationRepository;
import com.example.bankticketsystem.repository.ProductRepository;
import com.example.bankticketsystem.repository.UserProductAssignmentRepository;
import com.example.bankticketsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductServiceUpdateDeleteTest {

    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @Mock UserProductAssignmentRepository assignmentRepository;
    @Mock ApplicationRepository applicationRepository;

    @InjectMocks ProductService productService;

    UUID productId;
    UUID adminId;
    UUID ownerId;
    UUID otherId;
    Product product;
    User admin;
    User owner;
    User other;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        productId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        otherId = UUID.randomUUID();

        admin = new User(); admin.setId(adminId); admin.setRole(UserRole.ROLE_ADMIN);
        owner = new User(); owner.setId(ownerId); owner.setRole(UserRole.ROLE_USER);
        other = new User(); other.setId(otherId); other.setRole(UserRole.ROLE_USER);

        product = new Product(); product.setId(productId); product.setName("P"); product.setDescription("D");

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
    }

    @Test
    void adminCanUpdateProduct() {
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        ProductCreateRequest req = new ProductCreateRequest();
        req.setName("New name");
        req.setDescription("New desc");

        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        var dto = productService.updateProduct(productId, req, adminId);
        assertEquals("New name", dto.getName());
    }

    @Test
    void ownerCanUpdateProduct() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(assignmentRepository.existsByUserIdAndProductIdAndRoleOnProduct(ownerId, productId, AssignmentRole.PRODUCT_OWNER))
                .thenReturn(true);

        ProductCreateRequest req = new ProductCreateRequest();
        req.setName("Owner name");
        req.setDescription("Owner desc");

        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        var dto = productService.updateProduct(productId, req, ownerId);
        assertEquals("Owner name", dto.getName());
    }

    @Test
    void nonOwnerNonAdminCannotUpdateProduct() {
        when(userRepository.findById(otherId)).thenReturn(Optional.of(other));
        when(assignmentRepository.existsByUserIdAndProductIdAndRoleOnProduct(otherId, productId, AssignmentRole.PRODUCT_OWNER))
                .thenReturn(false);

        ProductCreateRequest req = new ProductCreateRequest();
        req.setName("X");
        req.setDescription("Y");

        assertThrows(ConflictException.class, () -> productService.updateProduct(productId, req, otherId));
    }

    @Test
    void deleteByAdminDeletesApplicationsAndProduct() {
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        Application a1 = new Application(); a1.setId(UUID.randomUUID());
        Application a2 = new Application(); a2.setId(UUID.randomUUID());
        when(applicationRepository.findByProductId(productId)).thenReturn(List.of(a1, a2));
        doNothing().when(applicationRepository).delete(any(Application.class));
        doNothing().when(assignmentRepository).deleteByProductId(productId);
        doNothing().when(productRepository).delete(product);

        productService.deleteProduct(productId, adminId);

        verify(applicationRepository, times(1)).findByProductId(productId);
        verify(applicationRepository, times(1)).delete(a1);
        verify(applicationRepository, times(1)).delete(a2);
        verify(assignmentRepository, times(1)).deleteByProductId(productId);
        verify(productRepository, times(1)).delete(product);
    }

    @Test
    void nonOwnerNonAdminCannotDelete() {
        when(userRepository.findById(otherId)).thenReturn(Optional.of(other));
        when(assignmentRepository.existsByUserIdAndProductIdAndRoleOnProduct(otherId, productId, AssignmentRole.PRODUCT_OWNER))
                .thenReturn(false);

        assertThrows(ConflictException.class, () -> productService.deleteProduct(productId, otherId));
    }
}
