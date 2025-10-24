package com.example.bankticketsystem.model.entity;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "tag", uniqueConstraints = @UniqueConstraint(columnNames = {"name"}))
public class Tag {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToMany(mappedBy = "tags")
    private Set<Application> applications = new HashSet<>();

    public Tag() {}

    // getters/setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Set<Application> getApplications() { return applications; }
    public void setApplications(Set<Application> applications) { this.applications = applications; }
}
