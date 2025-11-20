package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.dto.request.UserRequest;
import com.example.bankticketsystem.dto.response.UserResponse;
import com.example.bankticketsystem.exception.BadRequestException;
import com.example.bankticketsystem.repository.UserRepository;
import com.example.bankticketsystem.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final int MAX_PAGE_SIZE = 50;
    private final UserService userService;
    private final UserRepository userRepository;

    public UserController(UserService userService,
                          UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    // Create: POST “/api/v1/users” + UserCreateRequest (Body)
    @PostMapping
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRequest req) {
        UserResponse dto = userService.create(req);
        return ResponseEntity.status(201).body(dto);
    }

    // ReadAll: GET “api/v1/users?page=0&size=20”
    @GetMapping
    public ResponseEntity<List<UserResponse>> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   HttpServletResponse response) {
        if (size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size cannot be greater than " + MAX_PAGE_SIZE);
        }
        Page<UserResponse> u = userService.list(page, size);
        response.setHeader("X-Total-Count", String.valueOf(u.getTotalElements()));
        return ResponseEntity.ok(u.getContent());
    }

    // Read: GET “/api/v1/users/{id}”
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> showUser(@PathVariable("id") UUID id) {
        UserResponse userResponse = userService.get(id);
        return userResponse == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(userResponse);
    }

    // Update: PUT “/api/v1/users/{id}?actorId={adminId}” + UserCreateRequest (Body)
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable("id") UUID id,
                                                   @RequestParam("actorId") UUID actorId,
                                                   @Valid @RequestBody UserRequest req) {
        UserResponse updated = userService.updateUser(id, actorId, req);
        return ResponseEntity.ok(updated);
    }

    // Delete: DELETE “/api/v1/users/{id}?actorId={adminId}”
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable("id") UUID id,
                                           @RequestParam("actorId") UUID actorId) {
        userService.deleteUser(id, actorId);
        return ResponseEntity.noContent().build();
    }

    // PromoteToManager: PUT “/api/v1/users/{id}/promote-manager?actorId={adminId}”
    @PutMapping("/{id}/promote-manager")
    public ResponseEntity<Void> promoteManager(@PathVariable("id") UUID id,
                                               @RequestParam("actorId") UUID actorId) {
        userService.promoteToManager(id, actorId);
        return ResponseEntity.noContent().build();
    }

    // DemoteFromManager: PUT “/api/v1/users/{id}/demote-manager?actorId={adminId}”
    @PutMapping("/{id}/demote-manager")
    public ResponseEntity<Void> demoteManager(@PathVariable("id") UUID id,
                                              @RequestParam("actorId") UUID actorId) {
        userService.demoteToUser(id, actorId);
        return ResponseEntity.noContent().build();
    }
}
