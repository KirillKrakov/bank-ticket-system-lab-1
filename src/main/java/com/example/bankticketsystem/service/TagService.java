package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.TagDto;
import com.example.bankticketsystem.model.entity.Tag;
import com.example.bankticketsystem.repository.TagRepository;
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

    /**
     * Create tag if missing (returns existing if present).
     * Returns the entity (useful for internal operations).
     */
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

    /**
     * List all tags as DTOs.
     * Used by controller to return a safe view.
     */
    @Transactional(readOnly = true)
    public List<TagDto> listAll() {
        return repo.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private TagDto toDto(Tag t) {
        TagDto dto = new TagDto();
        dto.setId(t.getId());
        dto.setName(t.getName());
        return dto;
    }
}
