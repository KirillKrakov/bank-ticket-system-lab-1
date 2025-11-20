package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.dto.ApplicationDto;
import com.example.bankticketsystem.dto.TagDto;
import com.example.bankticketsystem.service.TagService;
import com.example.bankticketsystem.service.ApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagService tagService;
    private final ApplicationService applicationService;

    public TagController(TagService tagService, ApplicationService applicationService) {
        this.tagService = tagService;
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<TagDto> create(@Valid @RequestBody TagDto dto) {
        var created = tagService.createIfNotExists(dto.getName());
        TagDto out = new TagDto();
        out.setId(created.getId());
        out.setName(created.getName());
        return ResponseEntity.status(201).body(out);
    }

    @GetMapping
    public ResponseEntity<List<TagDto>> list() {
        List<TagDto> tags = tagService.listAll();
        return ResponseEntity.ok(tags);
    }

    @GetMapping("/{name}/applications")
    public ResponseEntity<TagDto> getTagWithApplications(@PathVariable String name) {
        TagDto tagWithApplications = tagService.getTagWithApplications(name);
        return ResponseEntity.ok(tagWithApplications);
    }
}
