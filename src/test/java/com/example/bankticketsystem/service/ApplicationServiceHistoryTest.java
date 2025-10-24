package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.ApplicationHistoryDto;
import com.example.bankticketsystem.exception.ConflictException;
import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.ApplicationHistory;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.ApplicationHistoryRepository;
import com.example.bankticketsystem.repository.ApplicationRepository;
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

class ApplicationServiceHistoryTest {

    @Mock ApplicationRepository appRepo;
    @Mock ApplicationHistoryRepository histRepo;
    @Mock UserRepository userRepo;
    @InjectMocks ApplicationService applicationService;

    UUID appId;
    UUID ownerId;
    UUID otherId;
    Application app;
    User owner;
    User other;
    ApplicationHistory hist;

    @BeforeEach
    void setUp(){
        MockitoAnnotations.openMocks(this);
        appId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        otherId = UUID.randomUUID();

        owner = new User(); owner.setId(ownerId); owner.setRole(UserRole.ROLE_USER);
        other = new User(); other.setId(otherId); other.setRole(UserRole.ROLE_USER);

        app = new Application(); app.setId(appId); app.setApplicant(owner);

        hist = new ApplicationHistory();
        hist.setId(UUID.randomUUID());
        hist.setApplication(app);
        hist.setOldStatus(null);
        hist.setNewStatus(null);
        hist.setChangedAt(Instant.now());

        when(appRepo.findById(appId)).thenReturn(Optional.of(app));
        when(histRepo.findByApplicationIdOrderByChangedAtDesc(appId)).thenReturn(List.of(hist));
    }

    @Test
    void ownerCanViewHistory() {
        when(userRepo.findById(ownerId)).thenReturn(Optional.of(owner));
        List<ApplicationHistoryDto> res = applicationService.listHistory(appId, ownerId);
        assertEquals(1, res.size());
        assertEquals(hist.getId(), res.get(0).getId());
    }

    @Test
    void nonOwnerNonManagerCannotView() {
        when(userRepo.findById(otherId)).thenReturn(Optional.of(other));
        assertThrows(ConflictException.class, () -> applicationService.listHistory(appId, otherId));
    }
}
