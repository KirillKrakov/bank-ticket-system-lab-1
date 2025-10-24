// src/main/java/com/example/bankticketsystem/dto/StatusChangeRequest.java
package com.example.bankticketsystem.dto;

import com.example.bankticketsystem.model.enums.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public class StatusChangeRequest {
    @NotNull
    private ApplicationStatus status;
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
}
