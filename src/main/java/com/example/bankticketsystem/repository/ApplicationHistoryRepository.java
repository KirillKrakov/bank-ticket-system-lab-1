package com.example.bankticketsystem.repository;

import com.example.bankticketsystem.model.entity.ApplicationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationHistoryRepository extends JpaRepository<ApplicationHistory, UUID> {
    List<ApplicationHistory> findByApplicationIdOrderByChangedAtDesc(UUID applicationId);
    void deleteByApplicationId(UUID applicationId);
}
