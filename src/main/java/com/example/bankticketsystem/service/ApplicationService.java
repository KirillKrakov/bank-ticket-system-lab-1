package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.*;
import com.example.bankticketsystem.model.entity.*;
import com.example.bankticketsystem.model.enums.ApplicationStatus;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.*;
import com.example.bankticketsystem.service.TagService;
import com.example.bankticketsystem.util.CursorUtil;
import com.example.bankticketsystem.exception.BadRequestException;
import com.example.bankticketsystem.exception.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final DocumentRepository documentRepository;
    private final ApplicationHistoryRepository historyRepository;
    private final TagService tagService;
    private final ApplicationHistoryRepository applicationHistoryRepository;

    public ApplicationService(ApplicationRepository applicationRepository,
                              UserRepository userRepository,
                              ProductRepository productRepository,
                              DocumentRepository documentRepository,
                              ApplicationHistoryRepository historyRepository,
                              TagService tagService,
                              ApplicationHistoryRepository applicationHistoryRepository) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
        this.tagService = tagService;
        this.applicationHistoryRepository = applicationHistoryRepository;
    }

    @Transactional
    public ApplicationDto createApplication(ApplicationCreateRequest req) {
        // validate applicant
        User applicant = userRepository.findById(req.getApplicantId())
                .orElseThrow(() -> new NotFoundException("Applicant not found"));

        Product product = productRepository.findById(req.getProductId())
                .orElseThrow(() -> new NotFoundException("Product not found"));

        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicant(applicant);
        app.setProduct(product);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(Instant.now());

        // handle documents metadata
        List<DocumentCreateRequest> docsReq = req.getDocuments() == null ? List.of() : req.getDocuments();
        List<Document> docs = new ArrayList<>();
        for (DocumentCreateRequest dreq : docsReq) {
            Document d = new Document();
            d.setId(UUID.randomUUID());
            d.setFileName(dreq.getFileName());
            d.setContentType(dreq.getContentType());
            d.setStoragePath(dreq.getStoragePath());
            d.setApplication(app);
            docs.add(d);
        }
        app.setDocuments(docs);

        applicationRepository.save(app); // cascades documents due to cascade ALL

        // write history
        ApplicationHistory hist = new ApplicationHistory();
        hist.setId(UUID.randomUUID());
        hist.setApplication(app);
        hist.setOldStatus(null);
        hist.setNewStatus(app.getStatus());
        hist.setChangedBy(applicant.getRole());
        hist.setChangedAt(Instant.now());
        historyRepository.save(hist);

        return toDto(app);
    }

    public Page<ApplicationDto> list(int page, int size) {
        Pageable p = PageRequest.of(page, size);
        return applicationRepository.findAll(p).map(this::toDto);
    }

    public ApplicationDto get(UUID id) {
        return applicationRepository.findById(id).map(this::toDto).orElse(null);
    }

    public Application getEntity(UUID id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Application not found: " + id));
    }

    private ApplicationDto toDto(Application app) {
        ApplicationDto dto = new ApplicationDto();
        dto.setId(app.getId());
        dto.setApplicantId(app.getApplicant() != null ? app.getApplicant().getId() : null);
        dto.setProductId(app.getProduct() != null ? app.getProduct().getId() : null);
        dto.setStatus(app.getStatus());
        dto.setCreatedAt(app.getCreatedAt());
        List<DocumentDto> docs = app.getDocuments().stream().map(d -> {
            DocumentDto dd = new DocumentDto();
            dd.setId(d.getId());
            dd.setFileName(d.getFileName());
            dd.setContentType(d.getContentType());
            dd.setStoragePath(d.getStoragePath());
            return dd;
        }).collect(Collectors.toList());
        List<String> tagNames = app.getTags() == null ? List.of() :
                app.getTags().stream().map(Tag::getName).toList();
        dto.setDocuments(docs);
        dto.setTags(tagNames);
        return dto;
    }

    // keyset stream
    public List<ApplicationDto> stream(String cursor, int limit) {
        CursorUtil.Decoded dec = CursorUtil.decode(cursor);
        Instant ts = dec == null ? null : dec.timestamp;
        UUID id = dec == null ? null : dec.id;
        int capped = Math.min(limit, 50);
        List<Application> apps = applicationRepository.findByKeyset(ts, id, capped);
        return apps.stream().map(this::toDto).collect(Collectors.toList());
    }

    // attach tags to application
    @Transactional
    public void attachTags(UUID applicationId, List<String> tagNames) {
        Application app = applicationRepository.findById(applicationId).orElseThrow(() -> new NotFoundException("Application not found"));
        for (String name : tagNames) {
            Tag t = tagService.createIfNotExists(name);
            app.getTags().add(t);
        }
        applicationRepository.save(app);
    }

    public Page<ApplicationDto> listByTag(String tagName, int page, int size) {
        Pageable p = PageRequest.of(page, size);
        Page<Application> apps = applicationRepository.findByTags_Name(tagName, p);
        return apps.map(this::toDto);
    }

    @Transactional
    public ApplicationDto changeStatus(UUID id, ApplicationStatus newStatus, UUID performedById) {
        if (newStatus == null) throw new BadRequestException("Status must be provided");

        Application app = getEntity(id);

        User performer = userRepository.findById(performedById)
                .orElseThrow(() -> new BadRequestException("Performer not found: " + performedById));

        // business rule: manager cannot change status of his own application
        if (performer.getRole() == UserRole.ROLE_MANAGER) {
            if (app.getApplicant() != null && app.getApplicant().getId().equals(performer.getId())) {
                throw new AccessDeniedException("Managers cannot change status of their own applications");
            }
        }

        ApplicationStatus oldStatus = app.getStatus();
        if (oldStatus == newStatus) {
            // ничего не меняется — возвращаем DTO
            return toDto(app);
        }

        try {
            // смена статуса
            app.setStatus(newStatus);
            app.setUpdatedAt(Instant.now());
            applicationRepository.save(app);

            // запись истории
            ApplicationHistory hist = new ApplicationHistory();
            hist.setId(UUID.randomUUID());
            hist.setApplication(app);
            hist.setOldStatus(oldStatus);
            hist.setNewStatus(newStatus);
            hist.setChangedBy(performer.getRole());
            hist.setChangedAt(Instant.now());

            applicationHistoryRepository.save(hist);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // логируем root cause в консоль (и возвращаем в тело ответа для отладки)
            Throwable root = ex.getRootCause() != null ? ex.getRootCause() : ex;
            root.printStackTrace();
            throw new com.example.bankticketsystem.exception.ConflictException("DB constraint violated: " + root.toString());
        }

        return toDto(app);
    }
}
