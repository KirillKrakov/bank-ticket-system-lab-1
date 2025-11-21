package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.ProductDto;
import com.example.bankticketsystem.dto.ProductRequest;
import com.example.bankticketsystem.exception.*;
import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.enums.AssignmentRole;
import com.example.bankticketsystem.model.enums.UserRole;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public class ProductAppManagementService {
    private final ProductService productService;
    private final ApplicationService applicationService;
    private final UserService userService;

    private final UserProductAssignmentService assignmentService;

    public ProductAppManagementService(ProductService productService, ApplicationService applicationService, UserService userService, UserProductAssignmentService assignmentService) {
        this.productService = productService;
        this.applicationService = applicationService;
        this.userService = userService;
        this.assignmentService = assignmentService;
    }

    public ProductDto updateProduct(UUID productId, ProductRequest req, UUID actorId) {
        if (req == null) throw new BadRequestException("Request is required");

        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        var actor = userService.findById(actorId);

        Product product = productService.findById(productId);

        boolean isAdmin = actor.getRole() == UserRole.ROLE_ADMIN;
        boolean isOwner = assignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER);

        if (!isAdmin && !isOwner) {
            throw new ForbiddenException("Only ADMIN or PRODUCT_OWNER can update product");
        }

        if (req.getName() != null) product.setName(req.getName());
        if (req.getDescription() != null) product.setDescription(req.getDescription());
        Product saved = productService.save(product);
        return productService.toDto(saved);
    }

    @Transactional
    public void deleteProduct(UUID productId, UUID actorId) {
        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        var actor = userService.findById(actorId);

        Product product = productService.findById(productId);

        boolean isAdmin = actor.getRole() == UserRole.ROLE_ADMIN;
        boolean isOwner = assignmentService.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER);

        if (!isAdmin && !isOwner) {
            throw new ForbiddenException("Only ADMIN or PRODUCT_OWNER can delete product");
        }

        try {
            List<Application> apps = applicationService.findByProductId(productId);
            for (Application a : apps) {
                applicationService.deleteApplication(a.getApplicant().getId(), a.getProduct().getId());
            }

            assignmentService.deleteByProductId(productId);

            productService.delete(product);
        } catch (Exception ex) {
            throw new ConflictException("Failed to delete product and its applications: " + ex.getMessage());
        }
    }
}
