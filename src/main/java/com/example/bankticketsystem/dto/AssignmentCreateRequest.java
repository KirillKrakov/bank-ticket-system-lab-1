package com.example.bankticketsystem.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import com.example.bankticketsystem.model.enums.AssignmentRole;

public class AssignmentCreateRequest {
    @NotNull private UUID userId;
    @NotNull private UUID productId;
    @NotNull private AssignmentRole role;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public AssignmentRole getRole() { return role; }
    public void setRole(AssignmentRole role) { this.role = role; }

}
