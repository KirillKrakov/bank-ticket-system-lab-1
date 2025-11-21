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
    private final UserService userService;
    private final ProductService productService;

    public UserProductAssignmentService(UserProductAssignmentRepository repo, UserService userService, ProductService productService) {
        this.repo = repo;
        this.userService = userService;
        this.productService = productService;
    }

    public boolean existsByUserIdAndProductIdAndRoleOnProduct(UUID userId, UUID productId, AssignmentRole role) {
        return repo.existsByUserIdAndProductIdAndRoleOnProduct(userId, productId, role);
    }

    public void deleteByProductId(UUID productId) {
        repo.deleteByProductId(productId);
    }

    public UserProductAssignment assign(UUID actorId, UUID userId, UUID productId, AssignmentRole role) {
        User u = userService.findById(userId);
        Product p = productService.findById(productId);

        var actor = userService.findById(actorId);
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
        var actor = userService.findById(actorId);

        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            throw new ForbiddenException("Only ADMIN can delete assignments!");
        }

        if (userId != null && productId != null) {
            User u = userService.findById(userId);
            Product p = productService.findById(productId);
            repo.deleteByUserIdAndProductId(userId, productId);
        } else if (userId != null) {
            User u = userService.findById(userId);
            repo.deleteByUserId(userId);
        } else if (productId != null) {
            Product p = productService.findById(productId);
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
}
