package com.example.bankticketsystem.repository;

import com.example.bankticketsystem.model.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
}
