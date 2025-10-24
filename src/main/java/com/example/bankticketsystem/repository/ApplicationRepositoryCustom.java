package com.example.bankticketsystem.repository;

import com.example.bankticketsystem.model.entity.Application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ApplicationRepositoryCustom {
    List<Application> findByKeyset(Instant ts, UUID id, int limit);
}
