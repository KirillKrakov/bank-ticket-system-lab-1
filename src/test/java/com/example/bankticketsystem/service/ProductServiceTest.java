package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.ProductDto;
import com.example.bankticketsystem.dto.ProductRequest;
import com.example.bankticketsystem.exception.BadRequestException;
import com.example.bankticketsystem.exception.ConflictException;
import com.example.bankticketsystem.exception.ForbiddenException;
import com.example.bankticketsystem.exception.NotFoundException;
import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.AssignmentRole;
import com.example.bankticketsystem.model.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ProductServiceTest {

    @Mock
    private ProductService productService;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private UserService userService;

    @Mock
    private UserProductAssignmentService assignmentService;

    private ProductAppManagementService productAppManagementService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        productAppManagementService = new ProductAppManagementService(
                productService, applicationService, userService, assignmentService
        );
    }

    // -----------------------
    // updateProduct tests
    // -----------------------
    @Test
    public void updateProduct_nullRequest_throwsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                productAppManagementService.updateProduct(UUID.randomUUID(), null, UUID.randomUUID()));
    }

    @Test
    public void updateProduct_actorNotFound_throwsNotFoundException() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(userService.findById(actorId)).thenThrow(new NotFoundException("User not found"));

        assertThrows(NotFoundException.class, () ->
                productAppManagementService.updateProduct(productId, new ProductRequest(), actorId));

        verify(userService, times(1)).findById(actorId);
    }

    @Test
    public void updateProduct_productNotFound_throwsNotFoundException() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        when(userService.findById(actorId)).thenReturn(actor);
        when(productService.findById(productId)).thenThrow(new NotFoundException("Product not found"));

        assertThrows(NotFoundException.class, () ->
                productAppManagementService.updateProduct(productId, new ProductRequest(), actorId));

        verify(productService, times(1)).findById(productId);
    }

    @Test
    public void updateProduct_actorNotAdminNorOwner_throwsForbidden() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_CLIENT);

        Product product = new Product();
        product.setId(productId);

        when(userService.findById(actorId)).thenReturn(actor);
        when(productService.findById(productId)).thenReturn(product);
        when(assignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER))
                .thenReturn(false);

        assertThrows(ForbiddenException.class, () ->
                productAppManagementService.updateProduct(productId, new ProductRequest(), actorId));
    }

    @Test
    public void updateProduct_asAdmin_updatesAndReturnsDto() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        Product existing = new Product();
        existing.setId(productId);
        existing.setName("old");
        existing.setDescription("oldDesc");

        ProductRequest req = new ProductRequest();
        req.setName("newName");
        req.setDescription("newDesc");

        Product saved = new Product();
        saved.setId(productId);
        saved.setName("newName");
        saved.setDescription("newDesc");

        ProductDto expectedDto = new ProductDto();
        expectedDto.setId(productId);
        expectedDto.setName("newName");
        expectedDto.setDescription("newDesc");

        when(userService.findById(actorId)).thenReturn(actor);
        when(productService.findById(productId)).thenReturn(existing);
        when(assignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER))
                .thenReturn(false); // admin doesn't need to be owner
        when(productService.save(any(Product.class))).thenReturn(saved);
        when(productService.toDto(saved)).thenReturn(expectedDto);

        ProductDto resp = productAppManagementService.updateProduct(productId, req, actorId);

        assertNotNull(resp);
        assertEquals("newName", resp.getName());
        assertEquals("newDesc", resp.getDescription());
        verify(productService, times(1)).save(any(Product.class));
    }

    @Test
    public void updateProduct_asOwner_updatesAndReturnsDto() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_CLIENT);

        Product existing = new Product();
        existing.setId(productId);
        existing.setName("old");
        existing.setDescription("oldDesc");

        ProductRequest req = new ProductRequest();
        req.setName("ownerName");
        req.setDescription("ownerDesc");

        Product saved = new Product();
        saved.setId(productId);
        saved.setName("ownerName");
        saved.setDescription("ownerDesc");

        ProductDto expectedDto = new ProductDto();
        expectedDto.setId(productId);
        expectedDto.setName("ownerName");
        expectedDto.setDescription("ownerDesc");

        when(userService.findById(actorId)).thenReturn(actor);
        when(productService.findById(productId)).thenReturn(existing);
        when(assignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER))
                .thenReturn(true);
        when(productService.save(any(Product.class))).thenReturn(saved);
        when(productService.toDto(saved)).thenReturn(expectedDto);

        ProductDto resp = productAppManagementService.updateProduct(productId, req, actorId);

        assertNotNull(resp);
        assertEquals("ownerName", resp.getName());
        verify(productService, times(1)).save(any(Product.class));
    }

    // -----------------------
    // deleteProduct tests
    // -----------------------
    @Test
    public void deleteProduct_actorNotFound_throwsNotFoundException() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(userService.findById(actorId)).thenThrow(new NotFoundException("User not found"));

        assertThrows(NotFoundException.class, () ->
                productAppManagementService.deleteProduct(productId, actorId));

        verify(userService, times(1)).findById(actorId);
    }

    @Test
    public void deleteProduct_productNotFound_throwsNotFoundException() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        when(userService.findById(actorId)).thenReturn(actor);
        when(productService.findById(productId)).thenThrow(new NotFoundException("Product not found"));

        assertThrows(NotFoundException.class, () ->
                productAppManagementService.deleteProduct(productId, actorId));

        verify(productService, times(1)).findById(productId);
    }

    @Test
    public void deleteProduct_actorNotAdminNorOwner_throwsForbidden() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_CLIENT);

        Product product = new Product();
        product.setId(productId);

        when(userService.findById(actorId)).thenReturn(actor);
        when(productService.findById(productId)).thenReturn(product);
        when(assignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER))
                .thenReturn(false);

        assertThrows(ForbiddenException.class, () ->
                productAppManagementService.deleteProduct(productId, actorId));
    }

    @Test
    public void deleteProduct_success_deletesApplicationsAssignmentsAndProduct() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        Product product = new Product();
        product.setId(productId);

        Application app1 = new Application();
        app1.setId(UUID.randomUUID());
        User app1User = new User();
        app1User.setId(UUID.randomUUID());
        app1.setApplicant(app1User);
        app1.setProduct(product);

        Application app2 = new Application();
        app2.setId(UUID.randomUUID());
        User app2User = new User();
        app2User.setId(UUID.randomUUID());
        app2.setApplicant(app2User);
        app2.setProduct(product);

        List<Application> apps = List.of(app1, app2);

        when(userService.findById(actorId)).thenReturn(actor);
        when(productService.findById(productId)).thenReturn(product);
        when(assignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER))
                .thenReturn(false); // admin is enough
        when(applicationService.findByProductId(productId)).thenReturn(apps);

        doNothing().when(applicationService).deleteApplication(any(UUID.class), any(UUID.class));
        doNothing().when(assignmentService).deleteByProductId(productId);
        doNothing().when(productService).delete(product);

        productAppManagementService.deleteProduct(productId, actorId);

        verify(applicationService, times(apps.size())).deleteApplication(any(UUID.class), any(UUID.class));
        verify(assignmentService, times(1)).deleteByProductId(productId);
        verify(productService, times(1)).delete(product);
    }

    @Test
    public void deleteProduct_deletionThrows_exceptionWrappedAsConflict() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        Product product = new Product();
        product.setId(productId);

        Application app = new Application();
        app.setId(UUID.randomUUID());
        User appUser = new User();
        appUser.setId(UUID.randomUUID());
        app.setApplicant(appUser);
        app.setProduct(product);

        when(userService.findById(actorId)).thenReturn(actor);
        when(productService.findById(productId)).thenReturn(product);
        when(applicationService.findByProductId(productId)).thenReturn(List.of(app));

        doThrow(new RuntimeException("db error")).when(applicationService)
                .deleteApplication(any(UUID.class), any(UUID.class));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                productAppManagementService.deleteProduct(productId, actorId));

        assertTrue(ex.getMessage().contains("Failed to delete product"));
    }
}
