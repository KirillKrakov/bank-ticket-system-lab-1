package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.UserCreateRequest;
import com.example.bankticketsystem.dto.UserDto;
import com.example.bankticketsystem.exception.BadRequestException;
import com.example.bankticketsystem.exception.ConflictException;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserDto create(UserCreateRequest req) {
        String username = req.getUsername().trim();
        String email = req.getEmail().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already in use");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already in use");
        }

        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setRole(UserRole.ROLE_USER); // default
        u.setCreatedAt(Instant.now());
        userRepository.save(u);

        return toDto(u);
    }

    public Page<UserDto> list(int page, int size) {
        Pageable p = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> users = userRepository.findAll(p);
        return users.map(this::toDto);
    }

    public Optional<UserDto> get(UUID id) {
        return userRepository.findById(id).map(this::toDto);
    }

    public UserDto toDto(User u) {
        UserDto dto = new UserDto();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setEmail(u.getEmail());
        dto.setRole(u.getRole());
        dto.setCreatedAt(u.getCreatedAt());
        return dto;
    }

    @Transactional
    public UserDto updateUser(UUID id, UserCreateRequest req) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("User not found"));

        existing.setUsername(req.getUsername());
        existing.setEmail(req.getEmail());
        existing.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        existing.setUpdatedAt(Instant.now());
        userRepository.save(existing);
        return toDto(existing);
    }

    @Transactional
    public void deleteUser(UUID id) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("User not found"));

        userRepository.delete(existing);
    }

    @Transactional
    public void promoteToManager(UUID id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("User not found"));
        if (u.getRole() == UserRole.ROLE_MANAGER) return;
        u.setRole(UserRole.ROLE_MANAGER);
        userRepository.save(u);
    }

    @Transactional
    public void demoteToUser(UUID id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("User not found"));
        if (u.getRole() == UserRole.ROLE_USER) return;
        u.setRole(UserRole.ROLE_USER);
        userRepository.save(u);
    }
}
