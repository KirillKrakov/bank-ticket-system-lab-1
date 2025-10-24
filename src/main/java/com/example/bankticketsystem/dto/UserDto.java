package com.example.bankticketsystem.dto;

import com.example.bankticketsystem.model.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

public class UserDto {
    private UUID id;
    private String username;
    private String email;
    private UserRole role;
    private Instant createdAt;

    // getters/setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
