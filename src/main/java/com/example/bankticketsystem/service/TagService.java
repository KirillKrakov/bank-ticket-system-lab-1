package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.ApplicationDto;
import com.example.bankticketsystem.dto.DocumentDto;
import com.example.bankticketsystem.dto.TagDto;
import com.example.bankticketsystem.exception.NotFoundException;
import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.entity.Tag;
import com.example.bankticketsystem.repository.TagRepository;
import io.swagger.v3.oas.annotations.tags.Tags;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TagService {

    private final TagRepository repo;
    public TagService(TagRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Tag createIfNotExists(String name) {
        return repo.findByName(name)
                .orElseGet(() -> {
                    Tag t = new Tag();
                    t.setId(UUID.randomUUID());
                    t.setName(name);
                    return repo.save(t);
                });
    }

    @Transactional(readOnly = true)
    public Page<TagDto> listAll(int page, int size) {
        Pageable p = PageRequest.of(page, size);
        Page<Tag> tags = repo.findAll(p);
        return tags.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public TagDto getTagWithApplications(String name) {
        Tag tag = repo.findByNameWithApplications(name)
                .orElseThrow(() -> new NotFoundException("Tag not found: " + name));

        TagDto dto = toDto(tag);

        List<ApplicationDto> applicationDtos = tag.getApplications().stream()
                .map(this::toApplicationDto)
                .collect(Collectors.toList());

        dto.setApplications(applicationDtos);
        return dto;
    }

    private ApplicationDto toApplicationDto(Application app) {
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

    private TagDto toDto(Tag t) {
        TagDto dto = new TagDto();
        dto.setId(UUID.randomUUID());
        dto.setName(t.getName());
        return dto;
    }
}
