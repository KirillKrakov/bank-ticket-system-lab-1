package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.ProductDto;
import com.example.bankticketsystem.dto.ProductRequest;
import com.example.bankticketsystem.exception.*;
import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.AssignmentRole;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product findById(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    public Product save(Product product){
        return productRepository.save(product);
    }

    public void delete(Product product) {
        productRepository.delete(product);
    }

    public ProductDto create(ProductRequest req) {
        if (req == null) throw new BadRequestException("Request is required");

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

    public ProductDto toDto(Product p) {
        ProductDto d = new ProductDto();
        d.setId(p.getId());
        d.setName(p.getName());
        d.setDescription(p.getDescription());
        return d;
    }


}
