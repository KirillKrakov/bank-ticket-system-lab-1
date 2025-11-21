package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.dto.TagDto;
import com.example.bankticketsystem.exception.BadRequestException;
import com.example.bankticketsystem.service.TagService;
import com.example.bankticketsystem.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Tag(name = "Tags", description = "API for managing tags")
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private static final int MAX_PAGE_SIZE = 50;
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
    public ResponseEntity<TagDto> create(@Valid @RequestBody String name, UriComponentsBuilder uriBuilder) {
        var created = tagService.createIfNotExists(name);
        TagDto out = new TagDto();
        out.setId(created.getId());
        out.setName(created.getName());
        URI location = uriBuilder.path("/api/v1/tags/{id}").buildAndExpand(out.getId()).toUri();
        return ResponseEntity.created(location).body(out);
    }

    // ReadAll: GET "/api/v1/tags?page=0&size=20"
    @Operation(summary = "Read all tags", description = "Returns list of tags")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of applications"),
            @ApiResponse(responseCode = "400", description = "Page size too large")
    })
    @GetMapping
    public ResponseEntity<List<TagDto>> list(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             HttpServletResponse response) {
        if (size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size cannot be greater than " + MAX_PAGE_SIZE);
        }
        Page<TagDto> p = tagService.listAll(page, size);
        response.setHeader("X-Total-Count", String.valueOf(p.getTotalElements()));
        return ResponseEntity.ok(p.getContent());
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
