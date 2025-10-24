package com.example.bankticketsystem.repository;

import com.example.bankticketsystem.model.entity.UserProductAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserProductAssignmentRepository extends JpaRepository<UserProductAssignment, UUID> {
    List<UserProductAssignment> findByUserId(UUID userId);
    List<UserProductAssignment> findByProductId(UUID productId);
}
