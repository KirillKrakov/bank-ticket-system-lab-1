package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.dto.request.UserRequest;
import com.example.bankticketsystem.dto.response.UserResponse;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.repository.UserRepository;
import com.example.bankticketsystem.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
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
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRequest req) {
        UserResponse dto = userService.create(req);
        return ResponseEntity.status(201).body(dto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable("id") UUID id,
                                                   @RequestParam("actorId") UUID actorId,
                                                   @Valid @RequestBody UserRequest req) {
        UserResponse updated = userService.updateUser(id, actorId, req);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable("id") UUID id,
                                           @RequestParam("actorId") UUID actorId) {
        userService.deleteUser(id, actorId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/promote-manager")
    public ResponseEntity<Void> promoteManager(@PathVariable("id") UUID id,
                                               @RequestParam("actorId") UUID actorId) {
        userService.promoteToManager(id, actorId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/demote-manager")
    public ResponseEntity<Void> demoteManager(@PathVariable("id") UUID id,
                                              @RequestParam("actorId") UUID actorId) {
        userService.demoteToUser(id, actorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> showUser(@PathVariable("id") UUID id) {
        User u = userRepository.findById(id).orElse(null);
        if (u == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(userService.toDto(u));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> listAll() {
        List<UserResponse> list = userRepository.findAll().stream()
                .map(userService::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }
}
