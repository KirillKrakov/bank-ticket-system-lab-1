package com.example.bankticketsystem.service;

import com.example.bankticketsystem.exception.ConflictException;
import com.example.bankticketsystem.exception.ForbiddenException;
import com.example.bankticketsystem.exception.UnauthorizedException;
import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public class UserAppManagementService {
    private final UserService userService;
    private final ApplicationService applicationService;

    public UserAppManagementService(UserService userService, ApplicationService applicationService) {
        this.userService = userService;
        this.applicationService = applicationService;
    }

    @Transactional
    public void deleteUser(UUID userId, UUID actorId) {
        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        User actor = userService.findById(actorId);
        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            throw new ForbiddenException("Only ADMIN can delete users");
        }

        User existing = userService.findById(actorId);

        try {
            List<Application> apps = applicationService.findByApplicantId(userId);
            for (Application a : apps) {
                applicationService.deleteApplication(a.getApplicant().getId(), a.getProduct().getId());
            }
            userService.deleteUser(existing);
        } catch (Exception ex) {
            throw new ConflictException("Failed to delete user and its applications: " + ex.getMessage());
        }
    }
}
