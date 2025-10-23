package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.dto.UserCreateRequest;
import com.example.bankticketsystem.dto.UserDto;
import com.example.bankticketsystem.service.UserService;
import com.example.bankticketsystem.exception.BadRequestException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final int MAX_PAGE_SIZE = 50;
    private final UserService userService;

    public UserController(UserService userService) { this.userService = userService; }

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody UserCreateRequest req, UriComponentsBuilder uriBuilder) {
        UserDto dto = userService.create(req);
        URI location = uriBuilder.path("/api/v1/users/{id}").buildAndExpand(dto.getId()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              HttpServletResponse response) {
        if (size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size cannot be greater than " + MAX_PAGE_SIZE);
        }
        Page<UserDto> p = userService.list(page, size);
        response.setHeader("X-Total-Count", String.valueOf(p.getTotalElements()));
        return ResponseEntity.ok(p.getContent());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> get(@PathVariable UUID id) {
        return userService.get(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new com.example.bankticketsystem.exception.NotFoundException("User not found"));
    }
}
