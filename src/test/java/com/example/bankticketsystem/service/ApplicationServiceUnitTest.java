package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.ApplicationCreateRequest;
import com.example.bankticketsystem.dto.DocumentCreateRequest;
import com.example.bankticketsystem.exception.NotFoundException;
import com.example.bankticketsystem.model.entity.*;
import com.example.bankticketsystem.model.enums.ApplicationStatus;
import com.example.bankticketsystem.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ApplicationServiceUnitTest {

    @Test
    void createApplication_success() {
        ApplicationRepository appRepo = Mockito.mock(ApplicationRepository.class);
        UserRepository userRepo = Mockito.mock(UserRepository.class);
        ProductRepository prodRepo = Mockito.mock(ProductRepository.class);
        DocumentRepository docRepo = Mockito.mock(DocumentRepository.class);
        ApplicationHistoryRepository histRepo = Mockito.mock(ApplicationHistoryRepository.class);
        TagService tagService = Mockito.mock(TagService.class);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("u1");
        user.setCreatedAt(Instant.now());
        when(userRepo.findById(any())).thenReturn(Optional.of(user));

        Product prod = new Product();
        prod.setId(UUID.randomUUID());
        prod.setName("prd");
        when(prodRepo.findById(any())).thenReturn(Optional.of(prod));

        when(appRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(histRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ApplicationHistoryRepository applicationHistoryRepository = Mockito.mock(ApplicationHistoryRepository.class);

        ApplicationService svc = new ApplicationService(appRepo, userRepo, prodRepo, docRepo, histRepo, tagService, applicationHistoryRepository);

        ApplicationCreateRequest req = new ApplicationCreateRequest();
        req.setApplicantId(user.getId());
        req.setProductId(prod.getId());
        DocumentCreateRequest d = new DocumentCreateRequest();
        d.setFileName("f.pdf");
        req.setDocuments(List.of(d));

        var dto = svc.createApplication(req);
        assertNotNull(dto.getId());
        assertEquals(ApplicationStatus.SUBMITTED, dto.getStatus());
        assertEquals(1, dto.getDocuments().size());
        verify(appRepo, times(1)).save(any());
        verify(histRepo, times(1)).save(any());
    }

    @Test
    void createApplication_missingApplicant_throws() {
        ApplicationRepository appRepo = Mockito.mock(ApplicationRepository.class);
        UserRepository userRepo = Mockito.mock(UserRepository.class);
        ProductRepository prodRepo = Mockito.mock(ProductRepository.class);
        DocumentRepository docRepo = Mockito.mock(DocumentRepository.class);
        ApplicationHistoryRepository histRepo = Mockito.mock(ApplicationHistoryRepository.class);
        TagService tagService = Mockito.mock(TagService.class);

        when(userRepo.findById(any())).thenReturn(Optional.empty());

        ApplicationHistoryRepository applicationHistoryRepository = Mockito.mock(ApplicationHistoryRepository.class);

        ApplicationService svc = new ApplicationService(appRepo, userRepo, prodRepo, docRepo, histRepo, tagService, applicationHistoryRepository);

        ApplicationCreateRequest req = new ApplicationCreateRequest();
        req.setApplicantId(UUID.randomUUID());
        req.setProductId(UUID.randomUUID());

        assertThrows(NotFoundException.class, () -> svc.createApplication(req));
        verify(appRepo, never()).save(any());
    }

    @Test
    void createApplication_historyThrows_exceptionPropagates() {
        ApplicationRepository appRepo = Mockito.mock(ApplicationRepository.class);
        UserRepository userRepo = Mockito.mock(UserRepository.class);
        ProductRepository prodRepo = Mockito.mock(ProductRepository.class);
        DocumentRepository docRepo = Mockito.mock(DocumentRepository.class);
        ApplicationHistoryRepository histRepo = Mockito.mock(ApplicationHistoryRepository.class);
        TagService tagService = Mockito.mock(TagService.class);

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("u1");
        user.setCreatedAt(Instant.now());
        when(userRepo.findById(any())).thenReturn(Optional.of(user));

        Product prod = new Product();
        prod.setId(UUID.randomUUID());
        prod.setName("prd");
        when(prodRepo.findById(any())).thenReturn(Optional.of(prod));

        when(appRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(histRepo.save(any())).thenThrow(new RuntimeException("DB error on history"));
        ApplicationHistoryRepository applicationHistoryRepository = Mockito.mock(ApplicationHistoryRepository.class);

        ApplicationService svc = new ApplicationService(appRepo, userRepo, prodRepo, docRepo, histRepo, tagService, applicationHistoryRepository);

        ApplicationCreateRequest req = new ApplicationCreateRequest();
        req.setApplicantId(user.getId());
        req.setProductId(prod.getId());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> svc.createApplication(req));
        assertTrue(ex.getMessage().contains("DB error on history"));
        // appRepo.save was called before exception (we mocked), rollback behavior is not tested here (unit test).
        verify(appRepo, times(1)).save(any());
        verify(histRepo, times(1)).save(any());
    }
}
