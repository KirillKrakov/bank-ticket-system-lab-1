package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.UserDto;
import com.example.bankticketsystem.dto.UserRequest;
import com.example.bankticketsystem.exception.*;
import com.example.bankticketsystem.model.entity.Application;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.ApplicationRepository;
import com.example.bankticketsystem.repository.UserRepository;
import com.password4j.Password;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User findById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public void deleteUser(User user) {
        userRepository.delete(user);
    }

    public UserDto create(UserRequest req) {
        if (req == null) throw new BadRequestException("Request is required");
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
        u.setPasswordHash(Password.hash(req.getPassword()).withBcrypt().getResult());
        u.setRole(UserRole.ROLE_CLIENT);
        u.setCreatedAt(Instant.now());
        userRepository.save(u);

        return toDto(u);
    }

    @Transactional(readOnly = true)
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

    public UserDto updateUser(UUID userId, UUID actorId, UserRequest req) {
        if (req == null) throw new BadRequestException("Request is required");
        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor not found: " + actorId));
        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            throw new ForbiddenException("Only ADMIN can update user info");
        }
        User existing = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        if (req.getUsername() != null) existing.setUsername(req.getUsername());
        if (req.getEmail() != null) existing.setEmail(req.getEmail());
        if (req.getPassword() != null) existing.setPasswordHash(Password.hash(req.getPassword()).withBcrypt().getResult());
        existing.setUpdatedAt(Instant.now());
        userRepository.save(existing);
        return toDto(existing);
    }

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

    public void demoteToUser(UUID id, UUID actorId) {
        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor not found: " + actorId));
        if (actor.getRole() != UserRole.ROLE_ADMIN) {
            throw new ForbiddenException("Only ADMIN can demote");
        }

        User u = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
        if (u.getRole() == UserRole.ROLE_CLIENT) return;
        u.setRole(UserRole.ROLE_CLIENT);
        userRepository.save(u);
    }
}
