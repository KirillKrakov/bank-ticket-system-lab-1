package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.UserDto;
import com.example.bankticketsystem.exception.ConflictException;
import com.example.bankticketsystem.exception.NotFoundException;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.ApplicationRepository;
import com.example.bankticketsystem.repository.UserRepository;
import com.password4j.Hash;
import com.password4j.HashBuilder;
import com.password4j.Password;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // -----------------------
    // createUser tests
    // -----------------------
    @Test
    void createUserSuccessCreatesUser() {
        UserDto req = new UserDto();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("StrongPass123");

        when(userRepository.existsByUsername(req.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<Password> mockedStatic = Mockito.mockStatic(Password.class)) {
            HashBuilder mockHashBuilder = mock(HashBuilder.class);
            Hash mockHash = mock(Hash.class);

            when(mockHash.getResult()).thenReturn("encodedPassword");
            when(mockHashBuilder.withBcrypt()).thenReturn(mockHash);

            mockedStatic.when(() -> Password.hash(anyString())).thenReturn(mockHashBuilder);

            var dto = userService.create(req);

            mockedStatic.verify(() -> Password.hash(req.getPassword()), times(1));

            verify(userRepository, times(1)).existsByUsername(req.getUsername());
            verify(userRepository, times(1)).existsByEmail(req.getEmail());
            verify(userRepository, times(1)).save(any(User.class));

            User saved = captor.getValue();
            assertEquals("alice", saved.getUsername());
            assertEquals("alice@example.com", saved.getEmail());
            assertEquals("encodedPassword", saved.getPasswordHash());
            assertEquals(UserRole.ROLE_USER, saved.getRole());
            assertNotNull(saved.getCreatedAt());

            assertEquals(saved.getId(), dto.getId());
            assertEquals("alice", dto.getUsername());
            assertEquals("alice@example.com", dto.getEmail());
            assertEquals(UserRole.ROLE_USER, dto.getRole());
        }
    }

    @Test
    void createUserDuplicateEmailThrowsConflict() {
        UserDto req = new UserDto();
        req.setUsername("bob");
        req.setEmail("bob@example.com");
        req.setPassword("pass12345");

        when(userRepository.existsByUsername(req.getUsername())).thenReturn(false); // username свободен
        when(userRepository.existsByEmail(req.getEmail())).thenReturn(true); // email занят

        assertThrows(ConflictException.class, () -> userService.create(req));

        InOrder inOrder = inOrder(userRepository);
        inOrder.verify(userRepository).existsByUsername(req.getUsername());
        inOrder.verify(userRepository).existsByEmail(req.getEmail());

        verify(userRepository, never()).save(any());
    }

    @Test
    void createUserDuplicateUsernameThrowsConflict() {
        UserDto req = new UserDto();
        req.setUsername("charlie");
        req.setEmail("charlie@example.com");
        req.setPassword("pass12345");

        when(userRepository.existsByUsername(req.getUsername())).thenReturn(true);

        assertThrows(ConflictException.class, () -> userService.create(req));

        verify(userRepository, times(1)).existsByUsername(req.getUsername());
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any());
    }

    // -----------------------
    // updateUser tests
    // -----------------------
    @Test
    void updateUserSuccessUpdatesFieldsAndPasswordAndUpdatedAt() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        User existing = new User();
        existing.setId(id);
        existing.setUsername("old");
        existing.setEmail("old@example.com");
        existing.setPasswordHash("oldhash");
        existing.setRole(UserRole.ROLE_USER);
        existing.setCreatedAt(Instant.now().minusSeconds(3600));

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDto req = new UserDto();
        req.setUsername("newname");
        req.setEmail("new@example.com");
        req.setPassword("newStrongPass123");

        try (MockedStatic<Password> mockedStatic = Mockito.mockStatic(Password.class)) {
            HashBuilder mockHashBuilder = mock(HashBuilder.class);
            Hash mockHash = mock(Hash.class);

            when(mockHash.getResult()).thenReturn("encodedHash");
            when(mockHashBuilder.withBcrypt()).thenReturn(mockHash);

            mockedStatic.when(() -> Password.hash(anyString())).thenReturn(mockHashBuilder);

            var dto = userService.updateUser(id, actorId, req);

            mockedStatic.verify(() -> Password.hash("newStrongPass123"), times(1));

            verify(userRepository, times(1)).findById(actorId);
            verify(userRepository, times(1)).findById(id);
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(1)).save(captor.capture());

            User saved = captor.getValue();
            assertEquals("newname", saved.getUsername());
            assertEquals("new@example.com", saved.getEmail());
            assertEquals("encodedHash", saved.getPasswordHash());
            assertNotNull(saved.getUpdatedAt());

            assertEquals(saved.getId(), dto.getId());
            assertEquals("newname", dto.getUsername());
            assertEquals("new@example.com", dto.getEmail());
            assertEquals(UserRole.ROLE_USER, dto.getRole());
        }
    }

    @Test
    void updateUserNotFoundThrowsBadRequest() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        UserDto req = new UserDto();
        req.setUsername("x");
        req.setEmail("x@example.com");
        req.setPassword("password123");

        assertThrows(NotFoundException.class, () -> userService.updateUser(id, actorId, req));

        verify(userRepository, times(1)).findById(actorId);
        verify(userRepository, times(1)).findById(id);
        verify(userRepository, never()).save(any());
    }

    // -----------------------
    // deleteUser tests
    // -----------------------
    @Test
    void deleteUserSuccessDeletesUser() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        User existing = new User();
        existing.setId(id);
        existing.setUsername("toDelete");

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        doNothing().when(userRepository).delete(existing);

        userService.deleteUser(id, actorId);

        verify(userRepository, times(1)).findById(actorId);
        verify(userRepository, times(1)).findById(id);
        verify(userRepository, times(1)).delete(existing);
    }

    @Test
    void deleteUserNotFoundThrowsBadRequest() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        when(applicationRepository.findByApplicantId(id)).thenReturn(List.of());

        assertThrows(NotFoundException.class, () -> userService.deleteUser(id, actorId));
        verify(userRepository, times(1)).findById(actorId);
        verify(userRepository, times(1)).findById(id);
        verify(userRepository, never()).delete(any());
    }

    // -----------------------
    // promoteToManager tests
    // -----------------------
    @Test
    void promoteToManagerExistingUserPromotesToManager() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        User u = new User();
        u.setId(id);
        u.setRole(UserRole.ROLE_USER);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.promoteToManager(id, actorId);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(UserRole.ROLE_MANAGER, saved.getRole());
    }

    @Test
    void promoteToManagerWhenAlreadyManagerNoSave() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        User u = new User();
        u.setId(id);
        u.setRole(UserRole.ROLE_MANAGER);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        userService.promoteToManager(id, actorId);

        verify(userRepository, times(1)).findById(actorId);
        verify(userRepository, times(1)).findById(id);
        verify(userRepository, never()).save(any());
    }

    @Test
    void promoteToManagerUserNotFoundThrowsBadRequest() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.promoteToManager(id, actorId));
        verify(userRepository, times(1)).findById(actorId);
        verify(userRepository, times(1)).findById(id);
        verify(userRepository, never()).save(any());
    }

    // -----------------------
    // demoteToUser tests
    // -----------------------
    @Test
    void demoteToUserExistingManagerDemotesToUser() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        User u = new User();
        u.setId(id);
        u.setRole(UserRole.ROLE_MANAGER);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.demoteToUser(id, actorId);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        User saved = captor.getValue();
        assertEquals(UserRole.ROLE_USER, saved.getRole());
    }

    @Test
    void demoteToUserWhenAlreadyUserNoSave() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        User u = new User();
        u.setId(id);
        u.setRole(UserRole.ROLE_USER);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        userService.demoteToUser(id, actorId);

        verify(userRepository, times(1)).findById(actorId);
        verify(userRepository, times(1)).findById(id);
        verify(userRepository, never()).save(any());
    }

    @Test
    void demoteToUserUserNotFoundThrowsBadRequest() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> userService.demoteToUser(id, actorId));
        verify(userRepository, times(1)).findById(actorId);
        verify(userRepository, times(1)).findById(id);
        verify(userRepository, never()).save(any());
    }
}
