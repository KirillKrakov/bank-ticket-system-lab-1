package com.example.bankticketsystem.repository;

import com.example.bankticketsystem.model.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID>, ApplicationRepositoryCustom {
    Page<Application> findAll(Pageable pageable);
    Page<Application> findByTags_Name(String tagName, Pageable pageable);
    long countByApplicantId(UUID applicantId);
    long countByProductId(UUID productId);

}
