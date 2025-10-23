package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.ProductCreateRequest;
import com.example.bankticketsystem.dto.ProductDto;
import com.example.bankticketsystem.model.entity.Product;
import com.example.bankticketsystem.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.*;

import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) { this.productRepository = productRepository; }

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
}
