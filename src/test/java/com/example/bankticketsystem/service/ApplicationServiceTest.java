package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.ApplicationHistoryDto;
import com.example.bankticketsystem.dto.ApplicationDto;
import com.example.bankticketsystem.dto.DocumentDto;
import com.example.bankticketsystem.exception.*;
import com.example.bankticketsystem.model.entity.*;
import com.example.bankticketsystem.model.enums.ApplicationStatus;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.*;
import com.example.bankticketsystem.util.ApplicationPage;
import com.example.bankticketsystem.util.CursorUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ApplicationServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private ApplicationHistoryRepository historyRepository;
    @Mock private TagService tagService;
    @Mock private ApplicationHistoryRepository applicationHistoryRepository;

    @InjectMocks private ApplicationService applicationService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        applicationService = new ApplicationService(applicationRepository, userRepository, productRepository,
                documentRepository, historyRepository, tagService, applicationHistoryRepository);
    }

    // -----------------------
    // createApplication tests
    // -----------------------
    @Test
    public void createApplication_nullRequest_throwsBadRequest() {
        assertThrows(BadRequestException.class, () -> applicationService.createApplication(null));
    }

    @Test
    public void createApplication_idOrCreatedAtPresent_throwsForbidden() {
        ApplicationDto req = new ApplicationDto();
        req.setId(UUID.randomUUID());
        assertThrows(ForbiddenException.class, () -> applicationService.createApplication(req));

        ApplicationDto req2 = new ApplicationDto();
        req2.setCreatedAt(Instant.now());
        assertThrows(ForbiddenException.class, () -> applicationService.createApplication(req2));
    }

    @Test
    public void createApplication_missingApplicantOrProduct_throwsBadRequest() {
        ApplicationDto req = new ApplicationDto();
        req.setApplicantId(null);
        req.setProductId(null);
        assertThrows(BadRequestException.class, () -> applicationService.createApplication(req));
    }

    @Test
    public void createApplication_statusProvided_throwsForbidden() {
        ApplicationDto req = new ApplicationDto();
        req.setApplicantId(UUID.randomUUID());
        req.setProductId(UUID.randomUUID());
        req.setStatus(ApplicationStatus.APPROVED);
        assertThrows(ForbiddenException.class, () -> applicationService.createApplication(req));
    }

    @Test
    public void createApplication_applicantNotFound_throwsNotFound() {
        UUID aid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ApplicationDto req = new ApplicationDto();
        req.setApplicantId(aid);
        req.setProductId(pid);

        when(userRepository.findById(aid)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> applicationService.createApplication(req));
    }

    @Test
    public void createApplication_productNotFound_throwsNotFound() {
        UUID aid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ApplicationDto req = new ApplicationDto();
        req.setApplicantId(aid);
        req.setProductId(pid);

        User user = new User();
        user.setId(aid);
        when(userRepository.findById(aid)).thenReturn(Optional.of(user));
        when(productRepository.findById(pid)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> applicationService.createApplication(req));
    }

    @Test
    public void createApplication_success_createsApplicationAndHistoryAndTags() {
        UUID aid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ApplicationDto req = new ApplicationDto();
        req.setApplicantId(aid);
        req.setProductId(pid);
        req.setTags(List.of("t1", "t2"));
        DocumentDto d = new DocumentDto();
        d.setFileName("f.txt");
        d.setContentType("text/plain");
        d.setStoragePath("/tmp/f");
        req.setDocuments(List.of(d));

        User user = new User();
        user.setId(aid);
        Product product = new Product();
        product.setId(pid);

        when(userRepository.findById(aid)).thenReturn(Optional.of(user));
        when(productRepository.findById(pid)).thenReturn(Optional.of(product));

        doAnswer(invocation -> {
            Application saved = invocation.getArgument(0);
            when(applicationRepository.findById(saved.getId())).thenReturn(Optional.of(saved));
            return saved;
        }).when(applicationRepository).save(any(Application.class));

        Tag tag1 = new Tag();
        tag1.setId(UUID.randomUUID());
        tag1.setName("t1");
        Tag tag2 = new Tag();
        tag2.setId(UUID.randomUUID());
        tag2.setName("t2");

        when(tagService.createIfNotExists("t1")).thenReturn(tag1);
        when(tagService.createIfNotExists("t2")).thenReturn(tag2);

        ApplicationDto result = applicationService.createApplication(req);

        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(aid, result.getApplicantId());
        assertEquals(pid, result.getProductId());
        assertEquals(1, result.getDocuments().size());
        verify(applicationRepository, times(2)).save(any(Application.class));
        verify(historyRepository, times(1)).save(any());
        verify(tagService, times(1)).createIfNotExists("t1");
        verify(tagService, times(1)).createIfNotExists("t2");
    }

    // -----------------------
    // ReadApplications tests
    // -----------------------
    @Test
    public void list_returnsPagedDto() {
        Application a1 = new Application();
        a1.setId(UUID.randomUUID());
        a1.setStatus(ApplicationStatus.SUBMITTED);
        Application a2 = new Application();
        a2.setId(UUID.randomUUID());
        a2.setStatus(ApplicationStatus.DRAFT);

        Page<Application> page = new PageImpl<>(List.of(a1, a2));
        when(applicationRepository.findAll(PageRequest.of(0, 10))).thenReturn(page);

        Page<ApplicationDto> res = applicationService.list(0, 10);
        assertEquals(2, res.getTotalElements());
        assertEquals(a1.getId(), res.getContent().get(0).getId());
    }

    @Test
    public void get_whenNotFound_returnsNull() {
        UUID id = UUID.randomUUID();
        when(applicationRepository.findById(id)).thenReturn(Optional.empty());
        assertNull(applicationService.get(id));
    }

    @Test
    public void get_whenFound_returnsDto() {
        UUID id = UUID.randomUUID();
        Application a = new Application();
        a.setId(id);
        a.setStatus(ApplicationStatus.DRAFT);
        when(applicationRepository.findById(id)).thenReturn(Optional.of(a));
        ApplicationDto dto = applicationService.get(id);
        assertNotNull(dto);
        assertEquals(id, dto.getId());
    }

    // -----------------------
    // streamApplications tests
    // -----------------------
    @Test
    public void streamWithNextCursor_callsFirstPageRepository_whenCursorIsNull() {
        Application a1 = new Application();
        a1.setId(UUID.randomUUID());
        a1.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));

        Application a2 = new Application();
        a2.setId(UUID.randomUUID());
        a2.setCreatedAt(Instant.parse("2024-01-01T00:00:10Z"));

        List<Application> apps = List.of(a1, a2);

        when(applicationRepository.findFirstPage(5)).thenReturn(apps);

        ApplicationPage page = applicationService.streamWithNextCursor(null, 5);

        assertNotNull(page);
        assertEquals(2, page.items().size());
        verify(applicationRepository, times(1)).findFirstPage(5);

        String expectedCursor = CursorUtil.encode(a2.getCreatedAt(), a2.getId());
        assertEquals(expectedCursor, page.nextCursor());
    }

    @Test
    public void streamWithNextCursor_callsFindByKeyset_whenCursorProvided() {
        CursorUtil.Decoded dec = new CursorUtil.Decoded(Instant.parse("2024-01-01T00:00:05Z"), UUID.randomUUID());
        String cursor = CursorUtil.encode(dec.timestamp, dec.id);

        Application a3 = new Application();
        a3.setId(UUID.randomUUID());
        a3.setCreatedAt(Instant.parse("2024-01-01T00:00:04Z"));

        when(applicationRepository.findByKeyset(dec.timestamp, dec.id, 5)).thenReturn(List.of(a3));

        ApplicationPage page = applicationService.streamWithNextCursor(cursor, 5);

        verify(applicationRepository, times(1)).findByKeyset(dec.timestamp, dec.id, 5);
        assertEquals(1, page.items().size());
        assertEquals(CursorUtil.encode(a3.getCreatedAt(), a3.getId()), page.nextCursor());
    }

    // -----------------------
    // attachTags tests
    // -----------------------
    @Test
    public void attachTags_actorIdNull_throwsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> applicationService.attachTags(UUID.randomUUID(), List.of("t"), null));
    }

    @Test
    public void attachTags_actorNotFound_throwsNotFound() {
        UUID actorId = UUID.randomUUID();
        when(userRepository.findById(actorId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> applicationService.attachTags(UUID.randomUUID(), List.of("t"), actorId));
    }

    @Test
    public void attachTags_applicationNotFound_throwsNotFound() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        User user = new User();
        user.setId(actorId);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(user));
        when(applicationRepository.findById(appId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> applicationService.attachTags(appId, List.of("t"), actorId));
    }

    @Test
    public void attachTags_notAllowed_throwsForbidden() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        User user = new User();
        user.setId(actorId);
        user.setRole(UserRole.ROLE_CLIENT);
        Application app = new Application();
        User applicant = new User();
        applicant.setId(UUID.randomUUID());
        app.setApplicant(applicant);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(user));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

        assertThrows(ForbiddenException.class, () -> applicationService.attachTags(appId, List.of("t"), actorId));
    }

    @Test
    public void attachTags_success_addsTagsAndSaves() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        User user = new User();
        user.setId(actorId);
        user.setRole(UserRole.ROLE_CLIENT);

        Application app = new Application();
        app.setId(appId);
        User applicant = new User();
        applicant.setId(actorId);
        app.setApplicant(applicant);
        app.setTags(new HashSet<>());

        when(userRepository.findById(actorId)).thenReturn(Optional.of(user));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        Tag t = new Tag();
        t.setId(UUID.randomUUID());
        t.setName("tag1");
        when(tagService.createIfNotExists("tag1")).thenReturn(t);

        applicationService.attachTags(appId, List.of("tag1"), actorId);

        assertTrue(app.getTags().stream().anyMatch(tag -> "tag1".equals(tag.getName())));
        verify(applicationRepository, times(1)).save(app);
    }

    // -----------------------
    // removeTags tests
    // -----------------------
    @Test
    public void removeTags_actorIdNull_throwsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> applicationService.removeTags(UUID.randomUUID(), List.of("t"), null));
    }

    @Test
    public void removeTags_actorNotFound_throwsNotFound() {
        UUID actorId = UUID.randomUUID();
        when(userRepository.findById(actorId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> applicationService.removeTags(UUID.randomUUID(), List.of("t"), actorId));
    }

    @Test
    public void removeTags_notAllowed_throwsForbidden() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        User user = new User();
        user.setId(actorId);
        user.setRole(UserRole.ROLE_CLIENT);

        Application app = new Application();
        User applicant = new User();
        applicant.setId(UUID.randomUUID());
        app.setApplicant(applicant);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(user));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

        assertThrows(ForbiddenException.class, () -> applicationService.removeTags(appId, List.of("t"), actorId));
    }

    @Test
    public void removeTags_success_removesSpecifiedTagsAndSaves() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        User user = new User();
        user.setId(actorId);
        user.setRole(UserRole.ROLE_CLIENT);

        Application app = new Application();
        app.setId(appId);
        User applicant = new User();
        applicant.setId(actorId);
        app.setApplicant(applicant);
        Tag tag1 = new Tag();
        tag1.setId(UUID.randomUUID());
        tag1.setName("a");
        Tag tag2 = new Tag();
        tag2.setId(UUID.randomUUID());
        tag2.setName("b");
        app.setTags(new HashSet<>(List.of(tag1, tag2)));

        when(userRepository.findById(actorId)).thenReturn(Optional.of(user));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

        applicationService.removeTags(appId, List.of("a"), actorId);

        assertTrue(app.getTags().stream().noneMatch(t -> "a".equals(t.getName())));
        verify(applicationRepository, times(1)).save(app);
    }

    // -----------------------
    // changeStatus tests
    // -----------------------
    @Test
    public void changeStatus_statusNull_throwsBadRequest() {
        assertThrows(BadRequestException.class, () -> applicationService.changeStatus(UUID.randomUUID(), null, UUID.randomUUID()));
    }

    @Test
    public void changeStatus_actorIdNull_throwsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> applicationService.changeStatus(UUID.randomUUID(), "APPROVED", null));
    }

    @Test
    public void changeStatus_actorNotFound_throwsNotFound() {
        UUID actorId = UUID.randomUUID();
        when(userRepository.findById(actorId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> applicationService.changeStatus(UUID.randomUUID(), "APPROVED", actorId));
    }

    @Test
    public void changeStatus_applicationNotFound_throwsNotFound() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(applicationRepository.findById(appId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> applicationService.changeStatus(appId, "APPROVED", actorId));
    }

    @Test
    public void changeStatus_actorNotAdminOrManager_throwsForbidden() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_CLIENT);

        Application app = new Application();
        app.setId(appId);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

        assertThrows(ForbiddenException.class, () -> applicationService.changeStatus(appId, "APPROVED", actorId));
    }

    @Test
    public void changeStatus_managerCannotChangeOwnApplication_throwsConflict() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_MANAGER);

        Application app = new Application();
        app.setId(appId);
        User applicant = new User();
        applicant.setId(actorId); // manager is also applicant
        app.setApplicant(applicant);
        app.setStatus(ApplicationStatus.SUBMITTED);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

        assertThrows(ConflictException.class, () -> applicationService.changeStatus(appId, "APPROVED", actorId));
    }

    @Test
    public void changeStatus_invalidStatus_throwsConflict() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        Application app = new Application();
        app.setId(appId);
        app.setStatus(ApplicationStatus.SUBMITTED);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

        assertThrows(ConflictException.class, () -> applicationService.changeStatus(appId, "NOT_EXIST", actorId));
    }

    @Test
    public void changeStatus_sameStatus_returnsDtoWithoutSaving() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        Application app = new Application();
        app.setId(appId);
        app.setStatus(ApplicationStatus.APPROVED);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

        ApplicationDto dto = applicationService.changeStatus(appId, "APPROVED", actorId);
        assertNotNull(dto);
        assertEquals(ApplicationStatus.APPROVED, dto.getStatus());
        verify(applicationRepository, never()).save(any());
        verify(applicationHistoryRepository, never()).save(any());
    }

    @Test
    public void changeStatus_adminSuccess_savesApplicationAndHistory() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        Application app = new Application();
        app.setId(appId);
        app.setStatus(ApplicationStatus.SUBMITTED);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(applicationHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationDto dto = applicationService.changeStatus(appId, "APPROVED", actorId);
        assertNotNull(dto);
        assertEquals(ApplicationStatus.APPROVED, dto.getStatus());
        verify(applicationRepository, times(1)).save(any(Application.class));
        verify(applicationHistoryRepository, times(1)).save(any());
    }

    @Test
    public void changeStatus_databaseConstraint_throwsConflictWithRootCause() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();

        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        Application app = new Application();
        app.setId(appId);
        app.setStatus(ApplicationStatus.SUBMITTED);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

        DataIntegrityViolationException dive = new DataIntegrityViolationException("constraint", new SQLException("FK failed"));
        when(applicationRepository.save(any(Application.class))).thenThrow(dive);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> applicationService.changeStatus(appId, "APPROVED", actorId));
        assertTrue(ex.getMessage().contains("DB constraint violated") || ex.getMessage().contains("DB constraint"));
    }

    // -----------------------
    // deleteApplication tests
    // -----------------------
    @Test
    public void deleteApplication_actorIdNull_throwsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> applicationService.deleteApplication(UUID.randomUUID(), null));
    }

    @Test
    public void deleteApplication_actorNotFound_throwsNotFound() {
        UUID actorId = UUID.randomUUID();
        when(userRepository.findById(actorId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> applicationService.deleteApplication(UUID.randomUUID(), actorId));
    }

    @Test
    public void deleteApplication_onlyAdminAllowed_throwsForbidden() {
        UUID actorId = UUID.randomUUID();
        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_CLIENT);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        assertThrows(ForbiddenException.class, () -> applicationService.deleteApplication(UUID.randomUUID(), actorId));
    }

    @Test
    public void deleteApplication_applicationNotFound_throwsNotFound() {
        UUID actorId = UUID.randomUUID();
        User admin = new User();
        admin.setId(actorId);
        admin.setRole(UserRole.ROLE_ADMIN);
        UUID appId = UUID.randomUUID();
        when(userRepository.findById(actorId)).thenReturn(Optional.of(admin));
        when(applicationRepository.findById(appId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> applicationService.deleteApplication(appId, actorId));
    }

    @Test
    public void deleteApplication_deleteFails_throwsConflict() {
        UUID actorId = UUID.randomUUID();
        User admin = new User();
        admin.setId(actorId);
        admin.setRole(UserRole.ROLE_ADMIN);
        Application app = new Application();
        app.setId(UUID.randomUUID());

        when(userRepository.findById(actorId)).thenReturn(Optional.of(admin));
        when(applicationRepository.findById(app.getId())).thenReturn(Optional.of(app));
        doThrow(new RuntimeException("fk")).when(applicationRepository).delete(app);

        ConflictException ex = assertThrows(ConflictException.class, () -> applicationService.deleteApplication(app.getId(), actorId));
        assertTrue(ex.getMessage().contains("Failed to delete application"));
    }

    @Test
    public void deleteApplication_success_callsDelete() {
        UUID actorId = UUID.randomUUID();
        User admin = new User();
        admin.setId(actorId);
        admin.setRole(UserRole.ROLE_ADMIN);
        Application app = new Application();
        app.setId(UUID.randomUUID());

        when(userRepository.findById(actorId)).thenReturn(Optional.of(admin));
        when(applicationRepository.findById(app.getId())).thenReturn(Optional.of(app));
        doNothing().when(applicationRepository).delete(app);

        applicationService.deleteApplication(app.getId(), actorId);

        verify(applicationRepository, times(1)).delete(app);
    }

    // -----------------------
    // listHistory tests
    // -----------------------
    @Test
    public void listHistory_actorIdNull_throwsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> applicationService.listHistory(UUID.randomUUID(), null));
    }

    @Test
    public void listHistory_actorNotFound_throwsNotFound() {
        UUID actorId = UUID.randomUUID();
        when(userRepository.findById(actorId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> applicationService.listHistory(UUID.randomUUID(), actorId));
    }

    @Test
    public void listHistory_applicationNotFound_throwsNotFound() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        User actor = new User();
        actor.setId(actorId);
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(applicationRepository.findById(appId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> applicationService.listHistory(appId, actorId));
    }

    @Test
    public void listHistory_notAllowed_throwsForbidden() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_CLIENT);

        Application app = new Application();
        User applicant = new User();
        applicant.setId(UUID.randomUUID());
        app.setApplicant(applicant);

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

        assertThrows(ForbiddenException.class, () -> applicationService.listHistory(appId, actorId));
    }

    @Test
    public void listHistory_success_returnsMappedDtos() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        Application app = new Application();
        app.setId(appId);

        ApplicationHistory h1 = new ApplicationHistory();
        h1.setId(UUID.randomUUID());
        h1.setApplication(app);
        h1.setOldStatus(null);
        h1.setNewStatus(ApplicationStatus.SUBMITTED);
        h1.setChangedBy(UserRole.ROLE_CLIENT);
        h1.setChangedAt(Instant.now());

        ApplicationHistory h2 = new ApplicationHistory();
        h2.setId(UUID.randomUUID());
        h2.setApplication(app);
        h2.setOldStatus(ApplicationStatus.SUBMITTED);
        h2.setNewStatus(ApplicationStatus.IN_REVIEW);
        h2.setChangedBy(UserRole.ROLE_ADMIN);
        h2.setChangedAt(Instant.now());

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(applicationHistoryRepository.findByApplicationIdOrderByChangedAtDesc(appId)).thenReturn(List.of(h2, h1));

        List<ApplicationHistoryDto> res = applicationService.listHistory(appId, actorId);
        assertEquals(2, res.size());
        assertEquals(h2.getId(), res.get(0).getId());
        assertEquals(h1.getId(), res.get(1).getId());
    }
}
