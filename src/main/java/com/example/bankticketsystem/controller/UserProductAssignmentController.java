package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.dto.UserProductAssignmentDto;
import com.example.bankticketsystem.service.UserProductAssignmentService;
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
import java.util.UUID;

@Tag(name = "User-Products Assignments", description = "API for managing assignments between users and products")
@RestController
@RequestMapping("/api/v1/assignments")
public class UserProductAssignmentController {

    private final UserProductAssignmentService svc;

    public UserProductAssignmentController(UserProductAssignmentService svc){ this.svc = svc; }

    // Create: POST "/api/v1/assignments" + UserProductAssignmentDto(userId,productId,role) (Body)
    @Operation(summary = "Create a new user-product assignment", description = "Registers a new many-to-many assignment between " +
            "user and product with additional field - the user's role in relation to the product if the actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Assignment created successfully"),
            @ApiResponse(responseCode = "403", description = "Insufficient level of actor's rights (not ADMIN or PRODUCT_OWNER)"),
            @ApiResponse(responseCode = "404", description = "User, product or actor with their ID are not found")
    })
    @PostMapping
    public ResponseEntity<UserProductAssignmentDto> assign(@Valid @RequestBody UserProductAssignmentDto req,
                                                           @RequestParam("actorId") UUID actorId,
                                                           UriComponentsBuilder uriBuilder) {
        var a = svc.assign(actorId, req.getUserId(), req.getProductId(), req.getRole());
        UserProductAssignmentDto dto = svc.toDto(a);
        URI location = uriBuilder.path("/api/v1/assignments/{id}").buildAndExpand(dto.getId()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    // Read: GET "/api/v1/assignments?userId={?}&productId={?}"
    @Operation(summary = "Read all user-product assignments", description = "Returns list of assignments between user and product: " +
            "id, userId, productId, role, assignedAt")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of user-product assignments"),
    })
    @GetMapping
    public ResponseEntity<List<UserProductAssignmentDto>> list(@RequestParam(required = false) UUID userId,
                                                               @RequestParam(required = false) UUID productId) {
        var list = svc.list(userId, productId);
        return ResponseEntity.ok(list);
    }

    // Delete: DELETE “/api/v1/assignments?actorId={?}&userId={?}&productId={?}"
    @Operation(summary = "Delete a specific assignment found by userId and productId", description = "Deletes one specific product from the database " +
            "if the actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Assignment deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Actor is unauthorized (actorId is null"),
            @ApiResponse(responseCode = "403", description = "Insufficient level of actor's rights to delete user (not ADMIN)"),
            @ApiResponse(responseCode = "404", description = "User, product or actor with their ID are not found")
    })
    @DeleteMapping
    public ResponseEntity<Void> deleteAssignments(
            @RequestParam UUID actorId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID productId) {

        svc.deleteAssignments(actorId, userId, productId);
        return ResponseEntity.noContent().build();
    }
}
