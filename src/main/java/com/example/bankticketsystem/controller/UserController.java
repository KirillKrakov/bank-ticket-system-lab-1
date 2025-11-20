package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.dto.UserDto;
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

    // Create: POST “/api/v1/users” + UserDto(username,email,password) (Body)
    @PostMapping
    public ResponseEntity<UserDto> register(@Valid @RequestBody UserDto req) {
        UserDto dto = userService.create(req);
        return ResponseEntity.status(201).body(dto);
    }

    // ReadAll: GET “api/v1/users?page=0&size=20”
    @GetMapping
    public ResponseEntity<List<UserDto>> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              HttpServletResponse response) {
        if (size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size cannot be greater than " + MAX_PAGE_SIZE);
        }
        Page<UserDto> u = userService.list(page, size);
        response.setHeader("X-Total-Count", String.valueOf(u.getTotalElements()));
        return ResponseEntity.ok(u.getContent());
    }

    // Read: GET “/api/v1/users/{id}”
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> showUser(@PathVariable("id") UUID id) {
        UserDto userDto = userService.get(id);
        return userDto == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(userDto);
    }

    // Update: PUT “/api/v1/users/{id}?actorId={adminId}” + UserDto(username,email,password) (Body)
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable("id") UUID id,
                                              @RequestParam("actorId") UUID actorId,
                                              @Valid @RequestBody UserDto req) {
        UserDto updated = userService.updateUser(id, actorId, req);
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
