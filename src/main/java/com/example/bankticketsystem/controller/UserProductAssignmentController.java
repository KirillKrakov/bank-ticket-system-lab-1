package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.dto.UserProductAssignmentDto;
import com.example.bankticketsystem.service.UserProductAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assignments")
public class UserProductAssignmentController {

    private final UserProductAssignmentService svc;

    public UserProductAssignmentController(UserProductAssignmentService svc){ this.svc = svc; }

    // Create: POST "/api/v1/assignments" + UserProductAssignmentDto(userId,productId,role) (Body)
    @PostMapping
    public ResponseEntity<UserProductAssignmentDto> assign(@Valid @RequestBody UserProductAssignmentDto req,
                                                           @RequestParam("actorId") UUID actorId) {

        var a = svc.assign(actorId, req.getUserId(), req.getProductId(), req.getRole());
        UserProductAssignmentDto dto = svc.toDto(a);
        return ResponseEntity.created(URI.create("/api/v1/assignments/" + a.getId())).body(dto);
    }

    // Read: GET "/api/v1/assignments?userId={?}&productId={?}"
    @GetMapping
    public ResponseEntity<List<UserProductAssignmentDto>> list(@RequestParam(required = false) UUID userId,
                                                               @RequestParam(required = false) UUID productId) {
        var list = svc.list(userId, productId);
        return ResponseEntity.ok(list);
    }

    // Delete: DELETE “/api/v1/assignments?actorId={?}&userId={?}&productId={?}"
    @DeleteMapping
    public ResponseEntity<Void> deleteAssignments(
            @RequestParam UUID actorId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID productId) {

        svc.deleteAssignments(actorId, userId, productId);
        return ResponseEntity.noContent().build();
    }
}
