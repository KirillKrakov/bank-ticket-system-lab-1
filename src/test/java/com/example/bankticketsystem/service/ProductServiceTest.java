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
import com.example.bankticketsystem.repository.ApplicationRepository;
import com.example.bankticketsystem.repository.ProductRepository;
import com.example.bankticketsystem.repository.UserProductAssignmentRepository;
import com.example.bankticketsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    // NOTE: UserRepository is no longer directly used by ProductService (UserService static is used),
    // but we keep the mock for any other needs / consistency in tests.
    @Mock
    private UserRepository userRepository;
    // UserProductAssignmentRepository is not used directly either (static UserProductAssignmentService is used),
    // but we keep it for backward compatibility in tests that might rely on it.
    @Mock
    private UserProductAssignmentRepository assignmentRepository;
    @Mock
    private ApplicationRepository applicationRepository;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        productService = new ProductService(productRepository);
    }

    // -----------------------
    // createProduct tests
    // -----------------------
    @Test
    public void create_nullRequest_throwsBadRequest() {
        assertThrows(BadRequestException.class, () -> productService.create(null));
        verifyNoInteractions(productRepository);
    }

    @Test
    public void create_missingName_throwsBadRequest() {
        ProductRequest req = new ProductRequest();
        req.setDescription("desc");
        assertThrows(BadRequestException.class, () -> productService.create(req));
        verifyNoInteractions(productRepository);
    }

    @Test
    public void create_missingDescription_throwsBadRequest() {
        ProductRequest req = new ProductRequest();
        req.setName("name");
        assertThrows(BadRequestException.class, () -> productService.create(req));
        verifyNoInteractions(productRepository);
    }

    @Test
    public void create_nameAlreadyExists_throwsConflict() {
        ProductRequest req = new ProductRequest();
        req.setName("product");
        req.setDescription("desc");

        when(productRepository.existsByName("product")).thenReturn(true);

        assertThrows(ConflictException.class, () -> productService.create(req));

        verify(productRepository, times(1)).existsByName("product");
        verify(productRepository, never()).save(any());
    }

    @Test
    public void create_success_returnsDto() {
        ProductRequest req = new ProductRequest();
        req.setName("product");
        req.setDescription("desc");

        Product saved = new Product();
        UUID id = UUID.randomUUID();
        saved.setId(id);
        saved.setName("product");
        saved.setDescription("desc");

        when(productRepository.existsByName("product")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductDto resp = productService.create(req);

        assertNotNull(resp);
        assertEquals(id, resp.getId());
        assertEquals("product", resp.getName());
        assertEquals("desc", resp.getDescription());

        verify(productRepository, times(1)).existsByName("product");
        verify(productRepository, times(1)).save(any(Product.class));
    }

    // -----------------------
    // readAllProducts tests
    // -----------------------
    @Test
    public void list_returnsPagedDto() {
        Product p1 = new Product();
        p1.setId(UUID.randomUUID());
        p1.setName("p1");
        p1.setDescription("d1");

        Product p2 = new Product();
        p2.setId(UUID.randomUUID());
        p2.setName("p2");
        p2.setDescription("d2");

        List<Product> list = List.of(p1, p2);
        Page<Product> page = new PageImpl<>(list);

        when(productRepository.findAll(PageRequest.of(0, 10))).thenReturn(page);

        Page<ProductDto> resp = productService.list(0, 10);

        assertEquals(2, resp.getTotalElements());
        assertEquals("p1", resp.getContent().get(0).getName());
        verify(productRepository, times(1)).findAll(PageRequest.of(0, 10));
    }

    // -----------------------
    // getProduct tests
    // -----------------------
    @Test
    public void get_whenNotFound_returnsNull() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        ProductDto resp = productService.get(id);
        assertNull(resp);
        verify(productRepository, times(1)).findById(id);
    }

    @Test
    public void get_whenFound_returnsDto() {
        UUID id = UUID.randomUUID();
        Product p = new Product();
        p.setId(id);
        p.setName("name");
        p.setDescription("desc");

        when(productRepository.findById(id)).thenReturn(Optional.of(p));

        ProductDto resp = productService.get(id);
        assertNotNull(resp);
        assertEquals(id, resp.getId());
        assertEquals("name", resp.getName());
    }

    // -----------------------
    // updateProduct tests
    // -----------------------
    @Test
    public void updateProduct_nullRequest_throwsBadRequest() {
        assertThrows(BadRequestException.class, () -> productService.updateProduct(UUID.randomUUID(), null, UUID.randomUUID()));
    }

    @Test
    public void updateProduct_actorNotFound_throwsBadRequest() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        try (MockedStatic<UserService> userStatic = Mockito.mockStatic(UserService.class)) {
            userStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.empty());
            assertThrows(NotFoundException.class, () -> productService.updateProduct(productId, new ProductRequest(), actorId));
            userStatic.verify(() -> UserService.findById(actorId), times(1));
        }
    }

    @Test
    public void updateProduct_productNotFound_throwsBadRequest() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        try (MockedStatic<UserService> userStatic = Mockito.mockStatic(UserService.class)) {
            userStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> productService.updateProduct(productId, new ProductRequest(), actorId));
            verify(productRepository, times(1)).findById(productId);
        }
    }

    @Test
    public void updateProduct_actorNotAdminNorOwner_throwsConflict() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_CLIENT);

        Product product = new Product();
        product.setId(productId);

        try (MockedStatic<UserService> userStatic = Mockito.mockStatic(UserService.class);
             MockedStatic<UserProductAssignmentService> assignStatic = Mockito.mockStatic(UserProductAssignmentService.class)) {

            userStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            assignStatic.when(() -> UserProductAssignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER))
                    .thenReturn(false);

            assertThrows(ForbiddenException.class, () -> productService.updateProduct(productId, new ProductRequest(), actorId));
            assignStatic.verify(() -> UserProductAssignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER), times(1));
        }
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

        try (MockedStatic<UserService> userStatic = Mockito.mockStatic(UserService.class);
             MockedStatic<UserProductAssignmentService> assignStatic = Mockito.mockStatic(UserProductAssignmentService.class)) {

            userStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
            when(productRepository.findById(productId)).thenReturn(Optional.of(existing));

            // <-- important: mock the static ownership check to avoid NPE on the static holder
            assignStatic.when(() -> UserProductAssignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER))
                    .thenReturn(false);

            when(productRepository.save(any(Product.class))).thenReturn(saved);

            ProductDto resp = productService.updateProduct(productId, req, actorId);

            assertNotNull(resp);
            assertEquals("newName", resp.getName());
            assertEquals("newDesc", resp.getDescription());
            verify(productRepository, times(1)).save(any(Product.class));

            // verify the static check was invoked once
            assignStatic.verify(() ->
                            UserProductAssignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER),
                    times(1)
            );
        }
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

        try (MockedStatic<UserService> userStatic = Mockito.mockStatic(UserService.class);
             MockedStatic<UserProductAssignmentService> assignStatic = Mockito.mockStatic(UserProductAssignmentService.class)) {

            userStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
            when(productRepository.findById(productId)).thenReturn(Optional.of(existing));
            assignStatic.when(() -> UserProductAssignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER))
                    .thenReturn(true);
            when(productRepository.save(any(Product.class))).thenReturn(saved);

            ProductDto resp = productService.updateProduct(productId, req, actorId);

            assertNotNull(resp);
            assertEquals("ownerName", resp.getName());
            verify(productRepository, times(1)).save(any(Product.class));
            assignStatic.verify(() -> UserProductAssignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER), times(1));
        }
    }

    // -----------------------
    // deleteProduct tests
    // -----------------------
    @Test
    public void deleteProduct_actorNotFound_throwsBadRequest() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        try (MockedStatic<UserService> userStatic = Mockito.mockStatic(UserService.class)) {
            userStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.empty());
            assertThrows(NotFoundException.class, () -> productService.deleteProduct(productId, actorId));
            userStatic.verify(() -> UserService.findById(actorId), times(1));
        }
    }

    @Test
    public void deleteProduct_productNotFound_throwsBadRequest() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        try (MockedStatic<UserService> userStatic = Mockito.mockStatic(UserService.class)) {
            userStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> productService.deleteProduct(productId, actorId));
            verify(productRepository, times(1)).findById(productId);
        }
    }

    @Test
    public void deleteProduct_actorNotAdminNorOwner_throwsConflict() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_CLIENT);

        Product product = new Product();
        product.setId(productId);

        try (MockedStatic<UserService> userStatic = Mockito.mockStatic(UserService.class);
             MockedStatic<UserProductAssignmentService> assignStatic = Mockito.mockStatic(UserProductAssignmentService.class)) {

            userStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            assignStatic.when(() -> UserProductAssignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER))
                    .thenReturn(false);

            assertThrows(ForbiddenException.class, () -> productService.deleteProduct(productId, actorId));
            assignStatic.verify(() -> UserProductAssignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER), times(1));
        }
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
        app1.setProduct(product);

        Application app2 = new Application();
        app2.setId(UUID.randomUUID());
        app2.setProduct(product);

        List<Application> apps = Arrays.asList(app1, app2);

        try (MockedStatic<UserService> userStatic = Mockito.mockStatic(UserService.class);
             MockedStatic<UserProductAssignmentService> assignStatic = Mockito.mockStatic(UserProductAssignmentService.class);
             MockedStatic<ApplicationService> appStatic = Mockito.mockStatic(ApplicationService.class)) {

            userStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            // owner-check returns false (admin is enough)
            assignStatic.when(() -> UserProductAssignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER))
                    .thenReturn(false);

            // ApplicationService.findByProductId returns apps
            appStatic.when(() -> ApplicationService.findByProductId(productId)).thenReturn(apps);
            // ApplicationService.delete - do nothing
            appStatic.when(() -> ApplicationService.delete(any(Application.class))).thenAnswer(inv -> null);
            // UserProductAssignmentService.deleteByProductId - do nothing
            assignStatic.when(() -> UserProductAssignmentService.deleteByProductId(productId)).thenAnswer(inv -> null);

            // productRepository.delete should work
            doNothing().when(productRepository).delete(product);

            productService.deleteProduct(productId, actorId);

            // verify ApplicationService.delete called for each app
            appStatic.verify(() -> ApplicationService.findByProductId(productId), times(1));
            appStatic.verify(() -> ApplicationService.delete(app1), times(1));
            appStatic.verify(() -> ApplicationService.delete(app2), times(1));

            assignStatic.verify(() -> UserProductAssignmentService.deleteByProductId(productId), times(1));
            verify(productRepository, times(1)).delete(product);
        }
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
        app.setProduct(product);

        try (MockedStatic<UserService> userStatic = Mockito.mockStatic(UserService.class);
             MockedStatic<ApplicationService> appStatic = Mockito.mockStatic(ApplicationService.class);
             MockedStatic<UserProductAssignmentService> assignStatic = Mockito.mockStatic(UserProductAssignmentService.class)) {

            userStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));
            appStatic.when(() -> ApplicationService.findByProductId(productId)).thenReturn(List.of(app));
            // simulate delete throwing
            appStatic.when(() -> ApplicationService.delete(any(Application.class))).thenThrow(new RuntimeException("db error"));

            ConflictException ex = assertThrows(ConflictException.class, () -> productService.deleteProduct(productId, actorId));
            assertTrue(ex.getMessage().contains("Failed to delete product"));
            appStatic.verify(() -> ApplicationService.delete(any(Application.class)), times(1));
        }
    }
}
