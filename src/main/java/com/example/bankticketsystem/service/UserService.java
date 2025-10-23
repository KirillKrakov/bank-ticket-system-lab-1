package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.UserCreateRequest;
import com.example.bankticketsystem.dto.UserDto;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.repository.UserRepository;
import com.example.bankticketsystem.exception.BadRequestException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDto create(UserCreateRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BadRequestException("Email already in use");
        }
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRoles("CLIENT"); // default role for now
        user.setCreatedAt(Instant.now());
        userRepository.save(user);
        return toDto(user);
    }

    public Page<UserDto> list(int page, int size) {
        Pageable p = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> users = userRepository.findAll(p);
        return users.map(this::toDto);
    }

    public Optional<UserDto> get(UUID id) {
        return userRepository.findById(id).map(this::toDto);
    }

    private UserDto toDto(User user) {
        UserDto d = new UserDto();
        d.setId(user.getId());
        d.setUsername(user.getUsername());
        d.setEmail(user.getEmail());
        d.setCreatedAt(user.getCreatedAt());
        return d;
    }
}
