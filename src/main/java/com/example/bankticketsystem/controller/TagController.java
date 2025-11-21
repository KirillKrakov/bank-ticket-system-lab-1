package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.dto.TagDto;
import com.example.bankticketsystem.service.TagService;
import com.example.bankticketsystem.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Tag(name = "Tags", description = "API for managing tags")
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagService tagService;
    private final ApplicationService applicationService;

    public TagController(TagService tagService, ApplicationService applicationService) {
        this.tagService = tagService;
        this.applicationService = applicationService;
    }

    // Create: POST "/api/v1/tags" + TagDto(name) (Body)
    @Operation(summary = "Create a new  unique tag", description = "Registers a new tag: name if it has not already existed")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tag created or found successfully")
    })
    @PostMapping
    public ResponseEntity<TagDto> create(@Valid @RequestBody TagDto dto, UriComponentsBuilder uriBuilder) {
        var created = tagService.createIfNotExists(dto.getName());
        TagDto out = new TagDto();
        out.setId(created.getId());
        out.setName(created.getName());
        URI location = uriBuilder.path("/api/v1/tags/{id}").buildAndExpand(out.getId()).toUri();
        return ResponseEntity.created(location).body(out);
    }

    // ReadAll: GET "/api/v1/tags"
    @Operation(summary = "Read all tags", description = "Returns list of tags")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of applications"),
    })
    @GetMapping
    public ResponseEntity<List<TagDto>> list() {
        List<TagDto> tags = tagService.listAll();
        return ResponseEntity.ok(tags);
    }

    // Read: GET "/api/v1/tags/{name}/applications"
    @Operation(summary = "Read certain tag by its name", description = "Returns data about a single tag: name and list of applications that uses this tag")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Data about a single tag"),
            @ApiResponse(responseCode = "404", description = "Tag with this name is not found")
    })
    @GetMapping("/{name}/applications")
    public ResponseEntity<TagDto> getTagWithApplications(@PathVariable String name) {
        TagDto tagWithApplications = tagService.getTagWithApplications(name);
        return ResponseEntity.ok(tagWithApplications);
    }
}
