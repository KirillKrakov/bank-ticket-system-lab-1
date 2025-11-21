package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.UserProductAssignmentDto;
import com.example.bankticketsystem.exception.*;
import com.example.bankticketsystem.model.entity.*;
import com.example.bankticketsystem.model.enums.AssignmentRole;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserProductAssignmentService {

    private final UserProductAssignmentRepository repo;
    // Статическое поле-холдер
    private static volatile UserProductAssignmentRepository STATIC_ASSIGNMENT_REPOSITORY;

    public UserProductAssignmentService(UserProductAssignmentRepository repo) {
        this.repo = repo;
        UserProductAssignmentService.STATIC_ASSIGNMENT_REPOSITORY = repo;
    }

    public UserProductAssignment assign(UUID actorId, UUID userId, UUID productId, AssignmentRole role) {
        User u = UserService.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        Product p = ProductService.findById(productId).orElseThrow(() -> new NotFoundException("Product not found"));

        var actor = UserService.findById(actorId).orElseThrow(() -> new NotFoundException("Actor not found: " + actorId));
        boolean isAdmin = actor.getRole() == UserRole.ROLE_ADMIN;
        boolean isOwner = repo.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER);

        if (!isAdmin && !isOwner) {
            throw new ForbiddenException("Only ADMIN or PRODUCT_OWNER can assign new products!");
        }

        Optional<UserProductAssignment> existingAssignment = repo.findByUserIdAndProductId(userId, productId);
        UserProductAssignment a = new UserProductAssignment();

        if (existingAssignment.isPresent()) {
            a = existingAssignment.get();
            a.setRoleOnProduct(role);
            a.setAssignedAt(Instant.now());
        } else {
            a.setId(UUID.randomUUID());
            a.setUser(u);
            a.setProduct(p);
            a.setRoleOnProduct(role);
            a.setAssignedAt(Instant.now());
        }
        return repo.save(a);
    }

    @Transactional(readOnly = true)
    public List<UserProductAssignmentDto> list(UUID userId, UUID productId) {
        List<UserProductAssignment> res;
        if (userId != null) res = repo.findByUserId(userId);
        else if (productId != null) res = repo.findByProductId(productId);
        else res = repo.findAll();
        return res.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public void deleteAssignments(UUID actorId, UUID userId, UUID productId) {
        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        var actor = UserService.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor not found: " + actorId));

        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            throw new ForbiddenException("Only ADMIN can delete assignments!");
        }

        if (userId != null && productId != null) {
            User u = UserService.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
            Product p = ProductService.findById(productId).orElseThrow(() -> new NotFoundException("Product not found"));
            repo.deleteByUserIdAndProductId(userId, productId);
        } else if (userId != null) {
            User u = UserService.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
            repo.deleteByUserId(userId);
        } else if (productId != null) {
            Product p = ProductService.findById(productId).orElseThrow(() -> new NotFoundException("Product not found"));
            repo.deleteByProductId(productId);
        } else {
            repo.deleteAll();
        }
    }

    public UserProductAssignmentDto toDto(UserProductAssignment a) {
        UserProductAssignmentDto dto = new UserProductAssignmentDto();
        dto.setId(a.getId());
        dto.setUserId(a.getUser() != null ? a.getUser().getId() : null);
        dto.setProductId(a.getProduct() != null ? a.getProduct().getId() : null);
        dto.setRole(a.getRoleOnProduct());
        dto.setAssignedAt(a.getAssignedAt());
        return dto;
    }

    public static boolean existsByUserIdAndProductIdAndRoleOnProduct(UUID actorId, UUID productId, AssignmentRole assignmentRole ) {
        return UserProductAssignmentService.STATIC_ASSIGNMENT_REPOSITORY.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, assignmentRole);
    }

    public static void deleteByProductId(UUID productId) {
        UserProductAssignmentService.STATIC_ASSIGNMENT_REPOSITORY.deleteByProductId(productId);
    }
}
