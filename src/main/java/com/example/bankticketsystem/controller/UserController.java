package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.dto.UserDto;
import com.example.bankticketsystem.exception.BadRequestException;
import com.example.bankticketsystem.repository.UserRepository;
import com.example.bankticketsystem.service.UserService;
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
import java.util.UUID;

@Tag(name = "Users", description = "API for managing users")
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
    @Operation(summary = "Create a new user", description = "Registers a new user: username, email, password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "403", description = "Forbidden input data: user ID and creation time"),
            @ApiResponse(responseCode = "409", description = "Username or email already in use")
    })
    @PostMapping
    public ResponseEntity<UserDto> register(@Valid @RequestBody UserDto req, UriComponentsBuilder uriBuilder) {
        UserDto dto = userService.create(req);
        URI location = uriBuilder.path("/api/v1/users/{id}").buildAndExpand(dto.getId()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    // ReadAll: GET “api/v1/users?page=0&size=20”
    @Operation(summary = "Read all users with pagination", description = "Returns a paginated list of users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of users"),
            @ApiResponse(responseCode = "400", description = "Page size too large")
    })
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
    @Operation(summary = "Read certain user by its ID", description = "Returns data about a single user: " +
            "ID, username, email, role, createdAt")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Data about a single user"),
            @ApiResponse(responseCode = "404", description = "User with this ID is not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> showUser(@PathVariable("id") UUID id) {
        UserDto userDto = userService.get(id);
        return userDto == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(userDto);
    }

    // Update: PUT “/api/v1/users/{id}?actorId={adminId}” + UserDto(username,email,password) (Body)
    @Operation(summary = "Update the data of a specific user found by ID", description = "Update any data of single user and returns it " +
            "if the actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Actor is unauthorized (actorId is null"),
            @ApiResponse(responseCode = "403", description = "Forbidden input data (like new user ID) or insufficient level of actor's rights (not ADMIN)"),
            @ApiResponse(responseCode = "404", description = "User or actor with their ID are not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable("id") UUID id,
                                              @RequestParam("actorId") UUID actorId,
                                              @Valid @RequestBody UserDto req) {
        UserDto updated = userService.updateUser(id, actorId, req);
        return ResponseEntity.ok(updated);
    }

    // Delete: DELETE “/api/v1/users/{id}?actorId={adminId}”
    @Operation(summary = "Delete a specific user found by ID", description = "Deletes one specific user from the database " +
            "if the actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "User deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Actor is unauthorized (actorId is null"),
            @ApiResponse(responseCode = "403", description = "Insufficient level of actor's rights to delete user (not ADMIN)"),
            @ApiResponse(responseCode = "404", description = "User or actor with their ID are not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable("id") UUID id,
                                           @RequestParam("actorId") UUID actorId) {
        userService.deleteUser(id, actorId);
        return ResponseEntity.noContent().build();
    }

    // PromoteToManager: PUT “/api/v1/users/{id}/promote-manager?actorId={adminId}”
    @Operation(summary = "Promote a specific user to a manager", description = "Change one specific user's role to MANAGER from USER")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "User promoted successfully"),
            @ApiResponse(responseCode = "401", description = "Actor is unauthorized (actorId is null"),
            @ApiResponse(responseCode = "403", description = "Insufficient level of actor's rights to delete user (not ADMIN)"),
            @ApiResponse(responseCode = "404", description = "User or actor with their ID are not found"),
            @ApiResponse(responseCode = "409", description = "An error occurred while cascading deletion of user and user-related applications")
    })
    @PutMapping("/{id}/promote-manager")
    public ResponseEntity<Void> promoteManager(@PathVariable("id") UUID id,
                                               @RequestParam("actorId") UUID actorId) {
        userService.promoteToManager(id, actorId);
        return ResponseEntity.noContent().build();
    }

    // DemoteFromManager: PUT “/api/v1/users/{id}/demote-manager?actorId={adminId}”
    @Operation(summary = "Demote a specific user from a manager", description = "Change one specific user's role to USER from MANAGER")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "User demoted successfully"),
            @ApiResponse(responseCode = "401", description = "Actor is unauthorized (actorId is null"),
            @ApiResponse(responseCode = "403", description = "Insufficient level of actor's rights to delete user (not ADMIN)"),
            @ApiResponse(responseCode = "404", description = "User or actor with their ID are not found")
    })
    @PutMapping("/{id}/demote-manager")
    public ResponseEntity<Void> demoteManager(@PathVariable("id") UUID id,
                                              @RequestParam("actorId") UUID actorId) {
        userService.demoteToUser(id, actorId);
        return ResponseEntity.noContent().build();
    }
}
