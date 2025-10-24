package com.example.bankticketsystem.service;

import com.example.bankticketsystem.dto.UserCreateRequest;
import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

public class UserServiceTest {

    @Test
    void createUser_success() {
        UserRepository repo = Mockito.mock(UserRepository.class);
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        Mockito.when(repo.existsByEmail("a@b.com")).thenReturn(false);
        Mockito.when(repo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserService svc = new UserService(repo, passwordEncoder);

        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("alice");
        req.setEmail("a@b.com");
        req.setPassword("password123");

        var dto = svc.create(req);
        assertNotNull(dto);
        assertEquals("alice", dto.getUsername());
        assertEquals("a@b.com", dto.getEmail());
        assertNotNull(dto.getId());
    }

    @Test
    void createUser_duplicateEmail_throws() {
        UserRepository repo = Mockito.mock(UserRepository.class);
        PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
        Mockito.when(repo.existsByEmail("a@b.com")).thenReturn(true);
        UserService svc = new UserService(repo, passwordEncoder);

        UserCreateRequest req = new UserCreateRequest();
        req.setUsername("alice");
        req.setEmail("a@b.com");
        req.setPassword("password123");

        assertThrows(RuntimeException.class, () -> svc.create(req));
    }
}
