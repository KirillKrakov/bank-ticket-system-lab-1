package com.example.bankticketsystem.model.entity;

import com.example.bankticketsystem.model.enums.UserRole;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "role")
public class RoleEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private UserRole name;

    @Column(length = 500)
    private String description;

    public RoleEntity() {}

    // getters/setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UserRole getName() { return name; }
    public void setName(UserRole name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
