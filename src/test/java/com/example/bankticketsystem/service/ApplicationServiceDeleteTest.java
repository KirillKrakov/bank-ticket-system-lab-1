package com.example.bankticketsystem.service;

import com.example.bankticketsystem.exception.ConflictException;
import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.ApplicationHistoryRepository;
import com.example.bankticketsystem.repository.ApplicationRepository;
import com.example.bankticketsystem.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationServiceDeleteTest {

    @Mock ApplicationRepository appRepo;
    @Mock ApplicationHistoryRepository histRepo;
    @Mock UserRepository userRepo;
    @InjectMocks ApplicationService applicationService;

    UUID appId;
    UUID adminId;
    UUID userId;
    Application application;
    User admin;
    User user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        appId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        userId = UUID.randomUUID();

        admin = new User(); admin.setId(adminId); admin.setRole(UserRole.ROLE_ADMIN);
        user = new User(); user.setId(userId); user.setRole(UserRole.ROLE_USER);

        application = new Application(); application.setId(appId);
        when(appRepo.findById(appId)).thenReturn(Optional.of(application));
    }

    @Test
    void nonAdminCannotDelete() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        assertThrows(ConflictException.class, () -> applicationService.deleteApplication(appId, userId));
    }

    @Test
    void adminDeletesSuccessfully() {
        when(userRepo.findById(adminId)).thenReturn(Optional.of(admin));
        // Do nothing on delete calls — verify they are invoked
        doNothing().when(appRepo).delete(application);

        applicationService.deleteApplication(appId, adminId);

        verify(appRepo, times(1)).delete(application);
    }

    @Test
    void deleteThrowsConflictIfDependentDeleteFails() {
        when(userRepo.findById(adminId)).thenReturn(Optional.of(admin));
        doThrow(new RuntimeException("fk error")).when(appRepo).delete(application);

        assertThrows(ConflictException.class, () -> applicationService.deleteApplication(appId, adminId));
    }
}
