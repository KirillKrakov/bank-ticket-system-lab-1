package com.example.bankticketsystem.repository;

import com.example.bankticketsystem.model.entity.ApplicationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApplicationHistoryRepository extends JpaRepository<ApplicationHistory, UUID> {
}
