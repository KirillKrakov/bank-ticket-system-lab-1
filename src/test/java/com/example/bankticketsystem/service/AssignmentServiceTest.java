package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.UserProductAssignmentDto;
import com.example.bankticketsystem.exception.ForbiddenException;
import com.example.bankticketsystem.exception.NotFoundException;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.entity.UserProductAssignment;
import com.example.bankticketsystem.model.enums.AssignmentRole;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.UserProductAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AssignmentServiceTest {

    @Mock
    private UserProductAssignmentRepository repo;

    @Mock
    private UserService userService;

    @Mock
    private ProductService productService;

    @InjectMocks
    private UserProductAssignmentService svc;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // -----------------------
    // assign tests
    // -----------------------
    @Test
    public void assign_createsNewAssignment_whenNoExisting_and_actorIsAdmin() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User(); actor.setId(actorId); actor.setRole(UserRole.ROLE_ADMIN);
        User user = new User(); user.setId(userId);
        Product product = new Product(); product.setId(productId);

        when(userService.findById(userId)).thenReturn(user);
        when(productService.findById(productId)).thenReturn(product);
        when(userService.findById(actorId)).thenReturn(actor);
        when(repo.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER)).thenReturn(false);
        when(repo.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.empty());
        when(repo.save(any(UserProductAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProductAssignment saved = svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER);

        assertNotNull(saved);
        assertEquals(userId, saved.getUser().getId());
        assertEquals(productId, saved.getProduct().getId());
        assertEquals(AssignmentRole.PRODUCT_OWNER, saved.getRoleOnProduct());
        verify(repo, times(1)).save(any(UserProductAssignment.class));
    }

    @Test
    public void assign_updatesExistingAssignment_whenFound_and_actorIsOwner() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User(); actor.setId(actorId); actor.setRole(UserRole.ROLE_CLIENT);
        User user = new User(); user.setId(userId);
        Product product = new Product(); product.setId(productId);

        UserProductAssignment existing = new UserProductAssignment();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setProduct(product);
        existing.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        existing.setAssignedAt(Instant.now().minusSeconds(3600));

        Instant oldAssignedAt = existing.getAssignedAt();

        when(userService.findById(userId)).thenReturn(user);
        when(productService.findById(productId)).thenReturn(product);
        when(userService.findById(actorId)).thenReturn(actor);
        when(repo.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER)).thenReturn(true);
        when(repo.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.of(existing));
        when(repo.save(any(UserProductAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProductAssignment result = svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER);

        assertNotNull(result);
        assertEquals(existing.getId(), result.getId());
        assertEquals(AssignmentRole.PRODUCT_OWNER, result.getRoleOnProduct());
        assertTrue(result.getAssignedAt().isAfter(oldAssignedAt));
        verify(repo, times(1)).save(existing);
    }

    @Test
    public void assign_throwsForbidden_whenActorIsNotAdminAndNotOwner() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User(); actor.setId(actorId); actor.setRole(UserRole.ROLE_CLIENT);
        User user = new User(); user.setId(userId);
        Product product = new Product(); product.setId(productId);

        when(userService.findById(userId)).thenReturn(user);
        when(productService.findById(productId)).thenReturn(product);
        when(userService.findById(actorId)).thenReturn(actor);
        when(repo.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER));

        verify(repo, never()).save(any());
    }

    // -----------------------
    // list tests
    // -----------------------
    @Test
    public void list_byUser_returnsMappedDtos() {
        UUID userId = UUID.randomUUID();
        User u = new User(); u.setId(userId);
        Product p = new Product(); p.setId(UUID.randomUUID());

        UserProductAssignment a = new UserProductAssignment();
        a.setId(UUID.randomUUID());
        a.setUser(u);
        a.setProduct(p);
        a.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        a.setAssignedAt(Instant.now());

        when(repo.findByUserId(userId)).thenReturn(List.of(a));

        List<UserProductAssignmentDto> dtos = svc.list(userId, null);

        assertEquals(1, dtos.size());
        UserProductAssignmentDto dto = dtos.get(0);
        assertEquals(a.getId(), dto.getId());
        assertEquals(userId, dto.getUserId());
        assertEquals(p.getId(), dto.getProductId());
        assertEquals(AssignmentRole.PRODUCT_OWNER, dto.getRole());
    }

    @Test
    public void list_byProduct_returnsMappedDtos() {
        UUID productId = UUID.randomUUID();
        User u = new User(); u.setId(UUID.randomUUID());
        Product p = new Product(); p.setId(productId);

        UserProductAssignment a = new UserProductAssignment();
        a.setId(UUID.randomUUID());
        a.setUser(u);
        a.setProduct(p);
        a.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        a.setAssignedAt(Instant.now());

        when(repo.findByProductId(productId)).thenReturn(List.of(a));

        List<UserProductAssignmentDto> dtos = svc.list(null, productId);

        assertEquals(1, dtos.size());
        UserProductAssignmentDto dto = dtos.get(0);
        assertEquals(a.getId(), dto.getId());
        assertEquals(u.getId(), dto.getUserId());
        assertEquals(productId, dto.getProductId());
        assertEquals(AssignmentRole.PRODUCT_OWNER, dto.getRole());
    }

    @Test
    public void list_all_returnsMappedDtos() {
        User u = new User(); u.setId(UUID.randomUUID());
        Product p = new Product(); p.setId(UUID.randomUUID());

        UserProductAssignment a = new UserProductAssignment();
        a.setId(UUID.randomUUID());
        a.setUser(u);
        a.setProduct(p);
        a.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        a.setAssignedAt(Instant.now());

        when(repo.findAll()).thenReturn(List.of(a));

        List<UserProductAssignmentDto> dtos = svc.list(null, null);
        assertEquals(1, dtos.size());
        assertEquals(a.getId(), dtos.get(0).getId());
    }

    // -----------------------
    // deleteAssignments tests
    // -----------------------
    @Test
    public void deleteAssignments_throwsNotFound_whenActorMissing() {
        UUID actorId = UUID.randomUUID();
        when(userService.findById(actorId)).thenThrow(new NotFoundException("actor not found"));
        assertThrows(NotFoundException.class, () -> svc.deleteAssignments(actorId, null, null));
    }

    @Test
    public void deleteAssignments_throwsForbidden_whenActorNotAdmin() {
        UUID actorId = UUID.randomUUID();
        User actor = new User(); actor.setId(actorId); actor.setRole(UserRole.ROLE_CLIENT);
        when(userService.findById(actorId)).thenReturn(actor);
        assertThrows(ForbiddenException.class, () -> svc.deleteAssignments(actorId, null, null));
    }

    @Test
    public void deleteAssignments_deleteSpecificAssignment_callsRepository_whenBothIdsProvided() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User admin = new User(); admin.setId(actorId); admin.setRole(UserRole.ROLE_ADMIN);
        User user = new User(); user.setId(userId);
        Product product = new Product(); product.setId(productId);

        when(userService.findById(actorId)).thenReturn(admin);
        when(userService.findById(userId)).thenReturn(user);
        when(productService.findById(productId)).thenReturn(product);

        doNothing().when(repo).deleteByUserIdAndProductId(userId, productId);

        svc.deleteAssignments(actorId, userId, productId);

        verify(repo, times(1)).deleteByUserIdAndProductId(userId, productId);
    }

    @Test
    public void deleteAssignments_deleteByUser_callsRepository_whenUserProvided() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User admin = new User(); admin.setId(actorId); admin.setRole(UserRole.ROLE_ADMIN);
        User user = new User(); user.setId(userId);

        when(userService.findById(actorId)).thenReturn(admin);
        when(userService.findById(userId)).thenReturn(user);

        doNothing().when(repo).deleteByUserId(userId);

        svc.deleteAssignments(actorId, userId, null);

        verify(repo, times(1)).deleteByUserId(userId);
    }

    @Test
    public void deleteAssignments_deleteByProduct_callsRepository_whenProductProvided() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User admin = new User(); admin.setId(actorId); admin.setRole(UserRole.ROLE_ADMIN);
        Product product = new Product(); product.setId(productId);

        when(userService.findById(actorId)).thenReturn(admin);
        when(productService.findById(productId)).thenReturn(product);

        doNothing().when(repo).deleteByProductId(productId);

        svc.deleteAssignments(actorId, null, productId);

        verify(repo, times(1)).deleteByProductId(productId);
    }

    @Test
    public void deleteAssignments_deleteAll_callsRepository_whenNoIdsProvided() {
        UUID actorId = UUID.randomUUID();
        User admin = new User(); admin.setId(actorId); admin.setRole(UserRole.ROLE_ADMIN);
        when(userService.findById(actorId)).thenReturn(admin);

        doNothing().when(repo).deleteAll();

        svc.deleteAssignments(actorId, null, null);

        verify(repo, times(1)).deleteAll();
    }

    // -----------------------
    // toDto tests
    // -----------------------
    @Test
    public void toDto_mapsAllFields() {
        User u = new User(); u.setId(UUID.randomUUID());
        Product p = new Product(); p.setId(UUID.randomUUID());
        UserProductAssignment a = new UserProductAssignment();
        a.setId(UUID.randomUUID());
        a.setUser(u);
        a.setProduct(p);
        a.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        a.setAssignedAt(Instant.now());

        UserProductAssignmentDto dto = svc.toDto(a);

        assertEquals(a.getId(), dto.getId());
        assertEquals(u.getId(), dto.getUserId());
        assertEquals(p.getId(), dto.getProductId());
        assertEquals(a.getRoleOnProduct(), dto.getRole());
        assertEquals(a.getAssignedAt(), dto.getAssignedAt());
    }
}
