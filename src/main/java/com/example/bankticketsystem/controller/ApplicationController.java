package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.dto.ApplicationCreateRequest;
import com.example.bankticketsystem.dto.ApplicationDto;
import com.example.bankticketsystem.dto.StatusChangeRequest;
import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.UserRepository;
import com.example.bankticketsystem.service.ApplicationService;
import com.example.bankticketsystem.exception.BadRequestException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.data.domain.Page;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private static final int MAX_PAGE_SIZE = 50;
    private final ApplicationService applicationService;
    private final UserRepository userRepository;

    public ApplicationController(ApplicationService applicationService, UserRepository userRepository) {
        this.applicationService = applicationService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<ApplicationDto> create(@Valid @RequestBody ApplicationCreateRequest req, UriComponentsBuilder uriBuilder) {
        ApplicationDto dto = applicationService.createApplication(req);
        URI location = uriBuilder.path("/api/v1/applications/{id}").buildAndExpand(dto.getId()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @GetMapping
    public ResponseEntity<List<ApplicationDto>> list(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     HttpServletResponse response) {
        if (size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size cannot be greater than " + MAX_PAGE_SIZE);
        }
        Page<ApplicationDto> p = applicationService.list(page, size);
        response.setHeader("X-Total-Count", String.valueOf(p.getTotalElements()));
        return ResponseEntity.ok(p.getContent());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationDto> get(@PathVariable UUID id) {
        ApplicationDto dto = applicationService.get(id);
        return dto == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(dto);
    }

    @GetMapping("/stream")
    public ResponseEntity<List<ApplicationDto>> stream(@RequestParam(required = false) String cursor,
                                                       @RequestParam(required = false, defaultValue = "20") int limit) {
        if (limit > 50) throw new BadRequestException("limit cannot be greater than 50");
        var list = applicationService.stream(cursor, limit);
        return ResponseEntity.ok(list);
    }

    @PostMapping("/{id}/tags")
    public ResponseEntity<Void> addTags(@PathVariable UUID id, @RequestBody List<String> tags) {
        applicationService.attachTags(id, tags);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public ResponseEntity<ApplicationDto> changeStatus(
            @PathVariable("id") UUID id,
            @RequestBody StatusChangeRequest req,
            Principal principal) {

        // basic validations
        if (req == null || req.getStatus() == null) {
            return ResponseEntity.badRequest().build();
        }

        // получаем текущего пользователя
        User current = userRepository.findByUsername(principal.getName()).orElseThrow(() ->
                new AccessDeniedException("Current user not found"));

        Application app = applicationService.getEntity(id); // метод, возвращающий entity
        if (app == null) return ResponseEntity.notFound().build();

        // проверка прав менеджера: если current является менеджером и владелец заявки - менеджер == current -> запрет
        if (current.getRole() == UserRole.ROLE_MANAGER) {
            if (app.getApplicant() != null && app.getApplicant().getId().equals(current.getId())) {
                // менеджер не может менять статус своей же заявки
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        // выполняем смену статуса в сервисе (внутри сервис должен делать запись в ApplicationHistory)
        ApplicationDto updated = applicationService.changeStatus(id, req.getStatus(), current.getId());
        return ResponseEntity.ok(updated);
    }
}
