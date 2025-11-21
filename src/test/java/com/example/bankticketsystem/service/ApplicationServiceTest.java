package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.*;
import com.example.bankticketsystem.exception.*;
import com.example.bankticketsystem.model.entity.*;
import com.example.bankticketsystem.model.entity.Tag;
import com.example.bankticketsystem.model.enums.ApplicationStatus;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.*;
import com.example.bankticketsystem.util.ApplicationPage;
import com.example.bankticketsystem.util.CursorUtil;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ApplicationServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationHistoryRepository applicationHistoryRepository;

    // We'll create the service manually (constructor takes two repos)
    private ApplicationService applicationService;

    // static mocks for static calls inside ApplicationService
    private MockedStatic<UserService> userServiceStatic;
    private MockedStatic<ProductService> productServiceStatic;
    private MockedStatic<TagService> tagServiceStatic;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // create service instance (this will also set STATIC_APPLICATION_REPOSITORY in ctor)
        applicationService = new ApplicationService(applicationRepository, applicationHistoryRepository);
    }

    @AfterEach
    public void tearDown() {
        if (userServiceStatic != null) userServiceStatic.close();
        if (productServiceStatic != null) productServiceStatic.close();
        if (tagServiceStatic != null) tagServiceStatic.close();
    }

    // -----------------------
    // createApplication tests
    // -----------------------
    @Test
    public void createApplication_nullRequest_throwsBadRequest() {
        assertThrows(BadRequestException.class, () -> applicationService.createApplication(null));
    }

    @Test
    public void createApplication_missingApplicantOrProduct_throwsBadRequest() {
        ApplicationRequest req = new ApplicationRequest();
        req.setApplicantId(null);
        req.setProductId(null);
        assertThrows(BadRequestException.class, () -> applicationService.createApplication(req));
    }

    @Test
    public void createApplication_applicantNotFound_throwsNotFound() {
        UUID aid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ApplicationRequest req = new ApplicationRequest();
        req.setApplicantId(aid);
        req.setProductId(pid);

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(aid)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> applicationService.createApplication(req));
    }

    @Test
    public void createApplication_productNotFound_throwsNotFound() {
        UUID aid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ApplicationRequest req = new ApplicationRequest();
        req.setApplicantId(aid);
        req.setProductId(pid);

        User user = new User();
        user.setId(aid);

        userServiceStatic = mockStatic(UserService.class);
        productServiceStatic = mockStatic(ProductService.class);

        userServiceStatic.when(() -> UserService.findById(aid)).thenReturn(Optional.of(user));
        productServiceStatic.when(() -> ProductService.findById(pid)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> applicationService.createApplication(req));
    }

    @Test
    public void createApplication_success_createsApplicationAndHistoryAndTags() {
        UUID aid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ApplicationRequest req = new ApplicationRequest();
        req.setApplicantId(aid);
        req.setProductId(pid);
        req.setTags(List.of("t1", "t2"));
        DocumentRequest d = new DocumentRequest();
        d.setFileName("f.txt");
        d.setContentType("text/plain");
        d.setStoragePath("/tmp/f");
        req.setDocuments(List.of(d));

        User user = new User();
        user.setId(aid);
        Product product = new Product();
        product.setId(pid);

        // --- static mocks (создаём до вызова метода)
        userServiceStatic = mockStatic(UserService.class);
        productServiceStatic = mockStatic(ProductService.class);
        tagServiceStatic = mockStatic(TagService.class);

        // гарантируем ответ для точного ID
        userServiceStatic.when(() -> UserService.findById(aid)).thenReturn(Optional.of(user));
        productServiceStatic.when(() -> ProductService.findById(pid)).thenReturn(Optional.of(product));

        // а также резервный ответ "на всякий случай" для любых входящих UUID
        userServiceStatic.when(() -> UserService.findById(Mockito.any()))
                .thenAnswer(inv -> Optional.of(user));
        productServiceStatic.when(() -> ProductService.findById(Mockito.any()))
                .thenAnswer(inv -> Optional.of(product));

        // Перехватываем сохранение application — вернём тот же объект и настроим findById(savedId) -> Optional.of(saved)
        // Для этого используем Answer, который подставит сохранённый объект в поведение findById
        final Application[] savedHolder = new Application[1];
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> {
            Application saved = inv.getArgument(0);
            if (saved.getId() == null) saved.setId(UUID.randomUUID());
            savedHolder[0] = saved;
            // stub findById for this saved id so attachTags/findById внутри сервиса увидят приложение
            when(applicationRepository.findById(saved.getId())).thenReturn(Optional.of(saved));
            return saved;
        });

        // history save
        when(applicationHistoryRepository.save(any(ApplicationHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        // TagService.createTag static behaviour — возвращаем объект Tag для каждого имени
        Tag tag1 = new Tag(); tag1.setId(UUID.randomUUID()); tag1.setName("t1");
        Tag tag2 = new Tag(); tag2.setId(UUID.randomUUID()); tag2.setName("t2");
        tagServiceStatic.when(() -> TagService.createTag("t1")).thenReturn(tag1);
        tagServiceStatic.when(() -> TagService.createTag("t2")).thenReturn(tag2);
        // и запасной ответ на любые имена (если вдруг)
        tagServiceStatic.when(() -> TagService.createTag(Mockito.anyString()))
                .thenAnswer(inv -> {
                    String name = inv.getArgument(0);
                    Tag t = new Tag(); t.setId(UUID.randomUUID()); t.setName(name); return t;
                });

        // Выполнение тестируемого метода
        ApplicationDto result = applicationService.createApplication(req);

        // Ассерты
        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(aid, result.getApplicantId());
        assertEquals(pid, result.getProductId());
        assertEquals(1, result.getDocuments().size());

        // Проверки взаимодействий
        verify(applicationRepository, atLeastOnce()).save(any(Application.class));
        verify(applicationHistoryRepository, times(1)).save(any(ApplicationHistory.class));
        // verify static tag creation
        tagServiceStatic.verify(() -> TagService.createTag("t1"), times(1));
        tagServiceStatic.verify(() -> TagService.createTag("t2"), times(1));
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
    // attachTags tests (now relying on UserService static)
    // -----------------------
    @Test
    public void attachTags_actorIdNull_throwsUnauthorized() {
        assertThrows(UnauthorizedException.class, () -> applicationService.attachTags(UUID.randomUUID(), List.of("t"), null));
    }

    @Test
    public void attachTags_actorNotFound_throwsNotFound() {
        UUID actorId = UUID.randomUUID();
        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> applicationService.attachTags(UUID.randomUUID(), List.of("t"), actorId));
    }

    @Test
    public void attachTags_applicationNotFound_throwsNotFound() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        User user = new User();
        user.setId(actorId);

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(user));
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

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(user));
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

        userServiceStatic = mockStatic(UserService.class);
        tagServiceStatic = mockStatic(TagService.class);

        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(user));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

        Tag t = new Tag();
        t.setId(UUID.randomUUID());
        t.setName("tag1");
        tagServiceStatic.when(() -> TagService.createTag("tag1")).thenReturn(t);

        applicationService.attachTags(appId, List.of("tag1"), actorId);

        assertTrue(app.getTags().stream().anyMatch(tag -> "tag1".equals(tag.getName())));
        verify(applicationRepository, times(1)).save(app);
        tagServiceStatic.verify(() -> TagService.createTag("tag1"), times(1));
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
        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.empty());
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

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(user));
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

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(user));
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
        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> applicationService.changeStatus(UUID.randomUUID(), "APPROVED", actorId));
    }

    @Test
    public void changeStatus_applicationNotFound_throwsNotFound() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_ADMIN);

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
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

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
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

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
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

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
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

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
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

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
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

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));

        DataIntegrityViolationException dive = new DataIntegrityViolationException("constraint", new SQLException("FK failed"));
        when(applicationRepository.save(any(Application.class))).thenThrow(dive);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> applicationService.changeStatus(appId, "APPROVED", actorId));
        assertTrue(ex.getMessage().toLowerCase().contains("db constraint") || ex.getMessage().toLowerCase().contains("constraint"));
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
        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> applicationService.deleteApplication(UUID.randomUUID(), actorId));
    }

    @Test
    public void deleteApplication_onlyAdminAllowed_throwsForbidden() {
        UUID actorId = UUID.randomUUID();
        User actor = new User();
        actor.setId(actorId);
        actor.setRole(UserRole.ROLE_CLIENT);

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));

        assertThrows(ForbiddenException.class, () -> applicationService.deleteApplication(UUID.randomUUID(), actorId));
    }

    @Test
    public void deleteApplication_applicationNotFound_throwsNotFound() {
        UUID actorId = UUID.randomUUID();
        User admin = new User();
        admin.setId(actorId);
        admin.setRole(UserRole.ROLE_ADMIN);
        UUID appId = UUID.randomUUID();

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(admin));
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

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(admin));
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

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(admin));
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
        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> applicationService.listHistory(UUID.randomUUID(), actorId));
    }

    @Test
    public void listHistory_applicationNotFound_throwsNotFound() {
        UUID actorId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        User actor = new User();
        actor.setId(actorId);

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
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

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
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

        userServiceStatic = mockStatic(UserService.class);
        userServiceStatic.when(() -> UserService.findById(actorId)).thenReturn(Optional.of(actor));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(applicationHistoryRepository.findByApplicationIdOrderByChangedAtDesc(appId)).thenReturn(List.of(h2, h1));

        List<ApplicationHistoryDto> res = applicationService.listHistory(appId, actorId);
        assertEquals(2, res.size());
        assertEquals(h2.getId(), res.get(0).getId());
        assertEquals(h1.getId(), res.get(1).getId());
    }
}
