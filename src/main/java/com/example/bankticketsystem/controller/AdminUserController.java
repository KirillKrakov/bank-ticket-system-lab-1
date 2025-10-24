package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.exception.BadRequestException;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.UserRepository;
import com.example.bankticketsystem.service.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminService adminService;
    private final UserRepository userRepository;

    public AdminUserController(AdminService adminService, UserRepository userRepository) {
        this.adminService = adminService;
        this.userRepository = userRepository;
    }

    @PostMapping("/{id}/promote-manager")
    public ResponseEntity<Void> promote(@PathVariable("id") UUID id,
                                        @RequestParam("actorId") UUID actorId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new BadRequestException("Actor (actorId) not found"));

        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        adminService.promoteToManager(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/demote-manager")
    public ResponseEntity<Void> demote(@PathVariable("id") UUID id,
                                       @RequestParam("actorId") UUID actorId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new BadRequestException("Actor (actorId) not found"));

        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        adminService.demoteToUser(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable("id") UUID id,
                                           @RequestParam("actorId") UUID actorId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new BadRequestException("Actor (actorId) not found"));

        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // adminService.deleteUser должен сам проверять зависимости и бросать ConflictException при необходимости
        adminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
