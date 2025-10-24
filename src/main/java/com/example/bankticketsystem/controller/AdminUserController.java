package com.example.bankticketsystem.controller;

import com.example.bankticketsystem.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminService adminService;

    public AdminUserController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/{id}/promote-manager")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> promote(@PathVariable("id") UUID id) {
        adminService.promoteToManager(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/demote-manager")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> demote(@PathVariable("id") UUID id) {
        adminService.demoteToUser(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable("id") UUID id) {
        adminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
