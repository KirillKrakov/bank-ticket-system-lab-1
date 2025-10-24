package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.ProductCreateRequest;
import com.example.bankticketsystem.dto.ProductDto;
import com.example.bankticketsystem.exception.BadRequestException;
import com.example.bankticketsystem.exception.ConflictException;
import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.entity.UserProductAssignment;
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

    public ProductDto create(ProductCreateRequest req) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p = productRepository.save(p);
        return toDto(p);
    }

    public Page<ProductDto> list(int page, int size) {
        Pageable p = PageRequest.of(page, size);
        return productRepository.findAll(p).map(this::toDto);
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

    /**
     * Update product name/description. Allowed for ADMIN or PRODUCT_OWNER assignment.
     */
    @Transactional
    public ProductDto updateProduct(UUID productId, ProductCreateRequest req, UUID actorId) {
        if (req == null) throw new BadRequestException("Request is required");

        var actor = userRepository.findById(actorId)
                .orElseThrow(() -> new BadRequestException("Actor not found: " + actorId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BadRequestException("Product not found: " + productId));

        // check permission: admin OR owner on product
        boolean isAdmin = actor.getRole() == UserRole.ROLE_ADMIN;
        boolean isOwner = assignmentRepository.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER);

        if (!isAdmin && !isOwner) {
            throw new ConflictException("Only ADMIN or PRODUCT_OWNER can update product");
        }

        // apply allowed changes
        product.setName(req.getName());
        product.setDescription(req.getDescription());
        Product saved = productRepository.save(product);
        return toDto(saved);
    }

    @Transactional
    public void deleteProduct(UUID productId, UUID actorId) {
        var actor = userRepository.findById(actorId)
                .orElseThrow(() -> new BadRequestException("Actor not found: " + actorId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BadRequestException("Product not found: " + productId));

        boolean isAdmin = actor.getRole() == UserRole.ROLE_ADMIN;
        boolean isOwner = assignmentRepository.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER);

        if (!isAdmin && !isOwner) {
            throw new ConflictException("Only ADMIN or PRODUCT_OWNER can delete product");
        }

        try {
            // find all applications for this product and delete them one-by-one to respect cascade on application -> history/documents
            List<Application> apps = applicationRepository.findByProductId(productId);
            for (Application a : apps) {
                applicationRepository.delete(a); // JPA will cascade delete history/documents (you have cascade = ALL there)
            }

            // delete assignments related to this product (clean up)
            assignmentRepository.deleteByProductId(productId);

            // finally delete product
            productRepository.delete(product);
        } catch (Exception ex) {
            throw new ConflictException("Failed to delete product and its applications: " + ex.getMessage());
        }
    }
}
