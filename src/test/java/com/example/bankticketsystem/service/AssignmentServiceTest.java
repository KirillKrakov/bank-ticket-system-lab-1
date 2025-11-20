package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.UserProductAssignmentDto;
import com.example.bankticketsystem.exception.BadRequestException;
import com.example.bankticketsystem.exception.ConflictException;
import com.example.bankticketsystem.exception.NotFoundException;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.entity.UserProductAssignment;
import com.example.bankticketsystem.model.enums.AssignmentRole;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.ProductRepository;
import com.example.bankticketsystem.repository.UserProductAssignmentRepository;
import com.example.bankticketsystem.repository.UserRepository;
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
    private UserRepository userRepo;

    @Mock
    private ProductRepository productRepo;

    @InjectMocks
    private UserProductAssignmentService svc;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        svc = new UserProductAssignmentService(repo, userRepo, productRepo);
    }

    // -----------------------
    // createAssignment tests
    // -----------------------
    @Test
    public void assign_createsNewAssignment_whenNoExisting_and_actorIsAdmin() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        User user = new User();
        user.setId(userId);

        Product product = new Product();
        product.setId(productId);

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(productRepo.findById(productId)).thenReturn(Optional.of(product));
        when(userRepo.findById(actorId)).thenReturn(Optional.of(actor));
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

    // -----------------------
    // updateAssignment tests
    // -----------------------
    @Test
    public void assign_updatesExistingAssignment_whenFound_and_actorIsOwner() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User(); actor.setId(actorId); actor.setRole(UserRole.ROLE_USER); // Изменено на не-админ, чтобы тестировать owner-сценарий
        User user = new User(); user.setId(userId);
        Product product = new Product(); product.setId(productId);

        UserProductAssignment existing = new UserProductAssignment();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setProduct(product);
        existing.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        existing.setAssignedAt(Instant.now().minusSeconds(3600));

        Instant oldAssignedAt = existing.getAssignedAt(); // Сохраняем старое время

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(productRepo.findById(productId)).thenReturn(Optional.of(product));
        when(userRepo.findById(actorId)).thenReturn(Optional.of(actor));
        when(repo.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER)).thenReturn(true);
        when(repo.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.of(existing));
        when(repo.save(any(UserProductAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        UserProductAssignment result = svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER);

        assertNotNull(result);
        assertEquals(existing.getId(), result.getId());
        assertEquals(AssignmentRole.PRODUCT_OWNER, result.getRoleOnProduct());
        assertTrue(result.getAssignedAt().isAfter(oldAssignedAt)); // Теперь сравниваем с сохранённым старым временем
        verify(repo, times(1)).save(existing);
    }

    // -----------------------
    // createAssignmentWhenNotAllowed tests
    // -----------------------
    @Test
    public void assign_throwsConflict_whenActorIsNotAdminAndNotOwner() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User actor = new User(); actor.setId(actorId); actor.setRole(UserRole.ROLE_USER);
        User user = new User(); user.setId(userId);
        Product product = new Product(); product.setId(productId);

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(productRepo.findById(productId)).thenReturn(Optional.of(product));
        when(userRepo.findById(actorId)).thenReturn(Optional.of(actor));
        when(repo.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER)).thenReturn(false);

        assertThrows(ConflictException.class, () -> svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER));

        verify(repo, never()).save(any());
    }

    @Test
    public void assign_throwsNotFound_whenUserMissing() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(userRepo.findById(userId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER));
    }

    @Test
    public void assign_throwsNotFound_whenProductMissing() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User user = new User(); user.setId(userId);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(productRepo.findById(productId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER));
    }

    @Test
    public void assign_throwsBadRequest_whenActorMissing() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User user = new User(); user.setId(userId);
        Product product = new Product(); product.setId(productId);

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(productRepo.findById(productId)).thenReturn(Optional.of(product));
        when(userRepo.findById(actorId)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER));
    }

    // -----------------------
    // readAssignment tests
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

    // -----------------------
    // readAllAssignments tests
    // -----------------------
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
    // deleteAssignment tests
    // -----------------------
    @Test
    public void deleteAssignments_throwsBadRequest_whenActorMissing() {
        UUID actorId = UUID.randomUUID();
        when(userRepo.findById(actorId)).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> svc.deleteAssignments(actorId, null, null));
    }

    @Test
    public void deleteAssignments_throwsConflict_whenActorNotAdmin() {
        UUID actorId = UUID.randomUUID();
        User actor = new User(); actor.setId(actorId); actor.setRole(UserRole.ROLE_USER);
        when(userRepo.findById(actorId)).thenReturn(Optional.of(actor));
        assertThrows(ConflictException.class, () -> svc.deleteAssignments(actorId, null, null));
    }

    @Test
    public void deleteAssignments_deleteSpecificAssignment_callsRepository_whenBothIdsProvided() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        User admin = new User(); admin.setId(actorId); admin.setRole(UserRole.ROLE_ADMIN);
        User user = new User(); user.setId(userId);
        Product product = new Product(); product.setId(productId);

        when(userRepo.findById(actorId)).thenReturn(Optional.of(admin));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(productRepo.findById(productId)).thenReturn(Optional.of(product));

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

        when(userRepo.findById(actorId)).thenReturn(Optional.of(admin));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

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

        when(userRepo.findById(actorId)).thenReturn(Optional.of(admin));
        when(productRepo.findById(productId)).thenReturn(Optional.of(product));

        doNothing().when(repo).deleteByProductId(productId);

        svc.deleteAssignments(actorId, null, productId);

        verify(repo, times(1)).deleteByProductId(productId);
    }

    @Test
    public void deleteAssignments_deleteAll_callsRepository_whenNoIdsProvided() {
        UUID actorId = UUID.randomUUID();
        User admin = new User(); admin.setId(actorId); admin.setRole(UserRole.ROLE_ADMIN);
        when(userRepo.findById(actorId)).thenReturn(Optional.of(admin));

        doNothing().when(repo).deleteAll();

        svc.deleteAssignments(actorId, null, null);

        verify(repo, times(1)).deleteAll();
    }

    @Test
    public void deleteAssignments_throwsNotFound_whenUserNotFound_forUserDelete() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User admin = new User(); admin.setId(actorId); admin.setRole(UserRole.ROLE_ADMIN);

        when(userRepo.findById(actorId)).thenReturn(Optional.of(admin));
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> svc.deleteAssignments(actorId, userId, null));
    }

    @Test
    public void deleteAssignments_throwsNotFound_whenProductNotFound_forProductDelete() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        User admin = new User(); admin.setId(actorId); admin.setRole(UserRole.ROLE_ADMIN);

        when(userRepo.findById(actorId)).thenReturn(Optional.of(admin));
        when(productRepo.findById(productId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> svc.deleteAssignments(actorId, null, productId));
    }

    @Test
    public void deleteAssignments_throwsNotFound_whenUserOrProductNotFound_forBothProvided() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        User admin = new User(); admin.setId(actorId); admin.setRole(UserRole.ROLE_ADMIN);

        when(userRepo.findById(actorId)).thenReturn(Optional.of(admin));
        when(userRepo.findById(userId)).thenReturn(Optional.empty());
        when(productRepo.findById(productId)).thenReturn(Optional.of(new Product()));

        assertThrows(NotFoundException.class, () -> svc.deleteAssignments(actorId, userId, productId));
    }

    // -----------------------
    // AssignmentToDto tests
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
