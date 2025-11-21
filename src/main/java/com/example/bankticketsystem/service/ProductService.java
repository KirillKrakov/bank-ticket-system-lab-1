package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.ProductDto;
import com.example.bankticketsystem.exception.*;
import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.enums.AssignmentRole;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.ApplicationRepository;
import com.example.bankticketsystem.repository.ProductRepository;
import com.example.bankticketsystem.repository.UserProductAssignmentRepository;
import com.example.bankticketsystem.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final UserProductAssignmentRepository assignmentRepository;
    private final ApplicationRepository applicationRepository;

    public ProductService(ProductRepository productRepository,
                          UserRepository userRepository,
                          UserProductAssignmentRepository assignmentRepository,
                          ApplicationRepository applicationRepository) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.applicationRepository = applicationRepository;
    }

    public ProductDto create(ProductDto req) {
        if (req == null) throw new BadRequestException("Request is required");
        if (req.getId() != null) {
            throw new ForbiddenException("Product ID sets automatically");
        }

        String name = req.getName();
        String description = req.getDescription();
        if (name == null || name.isEmpty()) {
            throw new BadRequestException("Product name must be in request body and not empty");
        }
        if (description == null || description.isEmpty()) {
            throw new BadRequestException("Product description must be in request body and not empty");
        }
        if (productRepository.existsByName(name.trim())) {
            throw new ConflictException("String already in use");
        }

        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p = productRepository.save(p);
        return toDto(p);
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> list(int page, int size) {
        Pageable p = PageRequest.of(page, size);
        Page<Product> products = productRepository.findAll(p);
        return products.map(this::toDto);
    }

    public ProductDto get(UUID id) {
        return productRepository.findById(id).map(this::toDto).orElse(null);
    }

    private ProductDto toDto(Product p) {
        ProductDto d = new ProductDto();
        d.setId(p.getId());
        d.setName(p.getName());
        d.setDescription(p.getDescription());
        return d;
    }

    public ProductDto updateProduct(UUID productId, ProductDto req, UUID actorId) {
        if (req == null) throw new BadRequestException("Request is required");
        if (req.getId() != null) {
            throw new ForbiddenException("Product ID has been already set automatically");
        }

        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        var actor = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor not found: " + actorId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));

        boolean isAdmin = actor.getRole() == UserRole.ROLE_ADMIN;
        boolean isOwner = assignmentRepository.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER);

        if (!isAdmin && !isOwner) {
            throw new ForbiddenException("Only ADMIN or PRODUCT_OWNER can update product");
        }

        if (req.getName() != null) product.setName(req.getName());
        if (req.getDescription() != null) product.setDescription(req.getDescription());
        Product saved = productRepository.save(product);
        return toDto(saved);
    }

    @Transactional
    public void deleteProduct(UUID productId, UUID actorId) {
        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        var actor = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor not found: " + actorId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));

        boolean isAdmin = actor.getRole() == UserRole.ROLE_ADMIN;
        boolean isOwner = assignmentRepository.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER);

        if (!isAdmin && !isOwner) {
            throw new ForbiddenException("Only ADMIN or PRODUCT_OWNER can delete product");
        }

        try {
            List<Application> apps = applicationRepository.findByProductId(productId);
            for (Application a : apps) {
                applicationRepository.delete(a);
            }

            assignmentRepository.deleteByProductId(productId);

            productRepository.delete(product);
        } catch (Exception ex) {
            throw new ConflictException("Failed to delete product and its applications: " + ex.getMessage());
        }
    }
}
