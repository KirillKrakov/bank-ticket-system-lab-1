package com.example.bankticketsystem.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import com.example.bankticketsystem.model.enums.AssignmentRole;

@Entity
@Table(name = "user_product_assignment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","product_id"}))
public class UserProductAssignment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_on_product", length = 100)
    private AssignmentRole roleOnProduct;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    public UserProductAssignment() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public AssignmentRole getRoleOnProduct() { return roleOnProduct; }
    public void setRoleOnProduct(AssignmentRole roleOnProduct) { this.roleOnProduct = roleOnProduct; }
    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
}
