package com.example.bankticketsystem.repository;

import com.example.bankticketsystem.model.entity.UserProductAssignment;
import com.example.bankticketsystem.model.enums.AssignmentRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProductAssignmentRepository extends JpaRepository<UserProductAssignment, UUID> {
    List<UserProductAssignment> findByUserId(UUID userId);
    List<UserProductAssignment> findByProductId(UUID productId);

    Optional<UserProductAssignment> findByUserIdAndProductId(UUID userId, UUID productId);
    long countByUserId(UUID userId);
    void deleteByProductId(UUID productId);
    boolean existsByUserIdAndProductIdAndRoleOnProduct(UUID actorId, UUID productId, AssignmentRole assignmentRole);
}
