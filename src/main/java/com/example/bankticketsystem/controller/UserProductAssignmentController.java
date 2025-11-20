package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.dto.AssignmentCreateRequest;
import com.example.bankticketsystem.dto.AssignmentDto;
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

    @PostMapping
    public ResponseEntity<AssignmentDto> assign(@Valid @RequestBody AssignmentCreateRequest req,
                                                @RequestParam("actorId") UUID actorId) {

        var a = svc.assign(actorId, req.getUserId(), req.getProductId(), req.getRole());
        AssignmentDto dto = svc.toDto(a);
        return ResponseEntity.created(URI.create("/api/v1/assignments/" + a.getId())).body(dto);
    }

    @GetMapping
    public ResponseEntity<List<AssignmentDto>> list(@RequestParam(required = false) UUID userId,
                                                    @RequestParam(required = false) UUID productId) {
        var list = svc.list(userId, productId);
        return ResponseEntity.ok(list);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAssignments(
            @RequestParam UUID actorId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID productId) {

        svc.deleteAssignments(actorId, userId, productId);
        return ResponseEntity.noContent().build();
    }
}
