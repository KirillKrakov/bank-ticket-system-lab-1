package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.UserDto;
import com.example.bankticketsystem.exception.*;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    public UserDto create(UserDto req) {
        if (req == null) throw new BadRequestException("Request is required");
        if (req.getId() != null || req.getCreatedAt() != null) {
            throw new ForbiddenException("User ID and time of user creation sets automatically");
        }
        if ((req.getUsername() == null) || (req.getEmail() == null) || (req.getPassword() == null)) {
            throw new BadRequestException("Username, email and password must be in request body");
        }

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
        u.setRole(UserRole.ROLE_USER);
        u.setCreatedAt(Instant.now());
        userRepository.save(u);

        return toDto(u);
    }

    public Page<UserDto> list(int page, int size) {
        Pageable p = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> users = userRepository.findAll(p);
        return users.map(this::toDto);
    }

    public UserDto get(UUID id) {
        return userRepository.findById(id).map(this::toDto).orElse(null);
    }

    public UserDto toDto(User u) {
        UserDto dto = new UserDto();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setEmail(u.getEmail());
        dto.setPassword("<Hidden>");
        dto.setRole(u.getRole());
        dto.setCreatedAt(u.getCreatedAt());
        return dto;
    }

    @Transactional
    public UserDto updateUser(UUID id, UUID actorId, UserDto req) {
        if (req == null) throw new BadRequestException("Request is required");
        if (req.getId() != null || req.getCreatedAt() != null) {
            throw new ForbiddenException("Product ID and time of product creation has been already set automatically");
        }
        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor not found: " + actorId));
        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            throw new ForbiddenException("Only ADMIN can update user info");
        }
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        if (req.getUsername() != null) existing.setUsername(req.getUsername());
        if (req.getEmail() != null) existing.setEmail(req.getEmail());
        if (req.getPassword() != null) existing.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        existing.setUpdatedAt(Instant.now());
        userRepository.save(existing);
        return toDto(existing);
    }

    @Transactional
    public void deleteUser(UUID id, UUID actorId) {
        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor not found: " + actorId));
        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            throw new ForbiddenException("Only ADMIN can delete users");
        }

        User existing = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        userRepository.delete(existing);
    }

    @Transactional
    public void promoteToManager(UUID id, UUID actorId) {
        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor not found: " + actorId));
        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            throw new ForbiddenException("Only ADMIN can promote");
        }

        User u = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
        if (u.getRole() == UserRole.ROLE_MANAGER) return;
        u.setRole(UserRole.ROLE_MANAGER);
        userRepository.save(u);
    }

    @Transactional
    public void demoteToUser(UUID id, UUID actorId) {
        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor not found: " + actorId));
        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            throw new ConflictException("Only ADMIN can demote");
        }

        User u = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
        if (u.getRole() == UserRole.ROLE_USER) return;
        u.setRole(UserRole.ROLE_USER);
        userRepository.save(u);
    }
}
