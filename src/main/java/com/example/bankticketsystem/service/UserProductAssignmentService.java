package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.AssignmentDto;
import com.example.bankticketsystem.exception.BadRequestException;
import com.example.bankticketsystem.exception.ConflictException;
import com.example.bankticketsystem.exception.NotFoundException;
import com.example.bankticketsystem.model.entity.*;
import com.example.bankticketsystem.model.enums.AssignmentRole;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserProductAssignmentService {

    private final UserProductAssignmentRepository repo;
    private final UserRepository userRepo;
    private final ProductRepository productRepo;

    public UserProductAssignmentService(UserProductAssignmentRepository repo,
                                        UserRepository userRepo,
                                        ProductRepository productRepo) {
        this.repo = repo; this.userRepo = userRepo; this.productRepo = productRepo;
    }

    public UserProductAssignment assign(UUID actorId, UUID userId, UUID productId, AssignmentRole role) {
        User u = userRepo.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        Product p = productRepo.findById(productId).orElseThrow(() -> new NotFoundException("Product not found"));

        var actor = userRepo.findById(actorId).orElseThrow(() -> new BadRequestException("Actor not found: " + actorId));
        boolean isAdmin = actor.getRole() == UserRole.ROLE_ADMIN;
        boolean isOwner = repo.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER);

        if (!isAdmin && !isOwner) {
            throw new ConflictException("Only ADMIN or PRODUCT_OWNER can assign new products!");
        }

        UserProductAssignment a = new UserProductAssignment();
        a.setId(UUID.randomUUID());
        a.setUser(u); a.setProduct(p); a.setRoleOnProduct(role); a.setAssignedAt(Instant.now());
        return repo.save(a);
    }

    public List<AssignmentDto> list(UUID userId, UUID productId) {
        List<UserProductAssignment> res;
        if (userId != null) res = repo.findByUserId(userId);
        else if (productId != null) res = repo.findByProductId(productId);
        else res = repo.findAll();
        return res.stream().map(this::toDto).collect(Collectors.toList());
    }

    public AssignmentDto toDto(UserProductAssignment a) {
        AssignmentDto dto = new AssignmentDto();
        dto.setId(a.getId());
        dto.setUserId(a.getUser() != null ? a.getUser().getId() : null);
        dto.setProductId(a.getProduct() != null ? a.getProduct().getId() : null);
        dto.setRole(a.getRoleOnProduct());
        dto.setAssignedAt(a.getAssignedAt());
        return dto;
    }
}
