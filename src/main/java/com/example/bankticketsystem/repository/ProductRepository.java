package com.example.bankticketsystem.repository;

import com.example.bankticketsystem.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    boolean existsByName(String name);
}
