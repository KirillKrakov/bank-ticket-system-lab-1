package com.example.bankticketsystem.service;

import com.example.bankticketsystem.exception.BadRequestException;
import com.example.bankticketsystem.exception.ConflictException;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.ApplicationRepository;
import com.example.bankticketsystem.repository.DocumentRepository;
import com.example.bankticketsystem.repository.UserProductAssignmentRepository;
import com.example.bankticketsystem.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final UserProductAssignmentRepository assignmentRepository;
    private final DocumentRepository documentRepository;

    public AdminService(UserRepository userRepository, ApplicationRepository applicationRepository, UserProductAssignmentRepository assignmentRepository, DocumentRepository documentRepository) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.assignmentRepository = assignmentRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public void promoteToManager(UUID userId) {
        User u = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (u.getRole() == UserRole.ROLE_ADMIN) return; // don't touch admins
        u.setRole(UserRole.ROLE_MANAGER);
        userRepository.save(u);
    }

    @Transactional
    public void demoteToUser(UUID userId) {
        User u = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (u.getRole() == UserRole.ROLE_ADMIN) return; // don't demote admins
        u.setRole(UserRole.ROLE_USER);
        userRepository.save(u);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new BadRequestException("User not found");
        }

        long apps = applicationRepository.countByApplicantId(userId);
        long assigns = assignmentRepository.countByUserId(userId);

        if (apps > 0 || assigns > 0) {
            String msg = String.format("User has dependencies: applications=%d, assignments=%d", apps, assigns);
            throw new ConflictException(msg);
        }

        userRepository.deleteById(userId);
    }
}
