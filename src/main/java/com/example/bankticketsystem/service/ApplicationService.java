package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.*;
import com.example.bankticketsystem.model.entity.*;
import com.example.bankticketsystem.model.enums.ApplicationStatus;
import com.example.bankticketsystem.repository.*;
import com.example.bankticketsystem.exception.BadRequestException;
import com.example.bankticketsystem.exception.NotFoundException;
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

    public ApplicationService(ApplicationRepository applicationRepository,
                              UserRepository userRepository,
                              ProductRepository productRepository,
                              DocumentRepository documentRepository,
                              ApplicationHistoryRepository historyRepository) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
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
        hist.setChangedBy(applicant.getUsername());
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
        dto.setDocuments(docs);
        return dto;
    }
}
