package com.example.bankticketsystem.repository;

import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {
    Optional<Tag> findByName(String name);

    @Query("SELECT t FROM Tag t LEFT JOIN FETCH t.applications WHERE t.name = :name")
    Optional<Tag> findByNameWithApplications(@Param("name") String name);
}
