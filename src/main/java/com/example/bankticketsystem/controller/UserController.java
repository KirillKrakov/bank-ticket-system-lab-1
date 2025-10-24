package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.dto.UserCreateRequest;
import com.example.bankticketsystem.dto.UserDto;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.repository.UserRepository;
import com.example.bankticketsystem.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    public UserController(UserService userService,
                          UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<UserDto> register(@Valid @RequestBody UserCreateRequest req) {
        UserDto dto = userService.create(req);
        return ResponseEntity.status(201).body(dto);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        String username = principal.getName();
        User u = userRepository.findByUsername(username).orElse(null);
        if (u == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(userService.toDto(u));
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> listAll() {
        List<UserDto> list = userRepository.findAll().stream()
                .map(userService::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }
}
