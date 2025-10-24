package com.example.bankticketsystem.dto;

import java.time.Instant;
import java.util.UUID;
import com.example.bankticketsystem.model.enums.AssignmentRole;

public class AssignmentDto {
    private UUID id;
    private UUID userId;
    private UUID productId;
    private AssignmentRole role;
    private Instant assignedAt;
    // getters/setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public AssignmentRole getRole() { return role; }
    public void setRole(AssignmentRole role) { this.role = role; }

    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }


}
