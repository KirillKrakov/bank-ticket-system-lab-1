package com.example.bankticketsystem.bootstrap;

import com.example.bankticketsystem.model.entity.User;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class AdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(UserRepository userRepository,
                            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing("Krakov Kirill", "kirill170251@gmail.com", "r0lling_st0ne1");
        createIfMissing("Babenko Daniil", "dbabenko@gmail.com", "r0lling_st0ne2");
    }

    private void createIfMissing(String username, String email, String plainPassword) {
        if (userRepository.findByEmail(email).isPresent()) return;
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername(username);
        u.setEmail(email.toLowerCase());
        u.setPasswordHash(passwordEncoder.encode(plainPassword));
        u.setRole(UserRole.ROLE_ADMIN);
        u.setCreatedAt(Instant.now());
        userRepository.save(u);
        System.out.println("Created admin: " + email);
    }
}
