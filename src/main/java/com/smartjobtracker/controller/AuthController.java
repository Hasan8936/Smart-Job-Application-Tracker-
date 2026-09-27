package com.smartjobtracker.controller;

import com.smartjobtracker.dto.AuthRequest;
import com.smartjobtracker.dto.AuthResponse;
import com.smartjobtracker.dto.RegisterRequest;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.security.JwtUtil;
import com.smartjobtracker.security.LoginAttemptLimiter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final com.smartjobtracker.service.SuperAdminBootstrap superAdmin;
    private final LoginAttemptLimiter loginLimiter;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                          com.smartjobtracker.service.SuperAdminBootstrap superAdmin, LoginAttemptLimiter loginLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.superAdmin = superAdmin;
        this.loginLimiter = loginLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        // Case-insensitive: the DB unique constraint is case-sensitive, and a case variant of an existing
        // address (e.g. the super admin's) must not become a second account.
        if (userRepository.existsByEmailIgnoreCase(req.getEmail().trim())) {
            return ResponseEntity.badRequest().body("Email already in use");
        }
        User u = new User();
        u.setName(req.getName());
        u.setEmail(req.getEmail().trim());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        userRepository.save(u);
        return ResponseEntity.created(URI.create("/api/auth/register")).build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest req) {
        if (loginLimiter.isBlocked(req.getEmail())) {
            return ResponseEntity.status(429).body("Too many failed sign-in attempts. Try again in a few minutes.");
        }
        return userRepository.findByEmail(req.getEmail()).map(u -> {
            if (!passwordEncoder.matches(req.getPassword(), u.getPasswordHash())) {
                loginLimiter.recordFailure(req.getEmail());
                return ResponseEntity.status(401).body("Invalid credentials");
            }
            loginLimiter.recordSuccess(req.getEmail());
            if (u.isSuspended()) {
                return ResponseEntity.status(403).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(com.smartjobtracker.security.JwtFilter.SUSPENDED_BODY);
            }
            superAdmin.promoteIfConfigured(u);
            u.setLastLoginAt(java.time.OffsetDateTime.now());
            userRepository.save(u);
            String token = jwtUtil.generateToken(u.getEmail());
            return ResponseEntity.ok(new AuthResponse(token));
        }).orElseGet(() -> {
            loginLimiter.recordFailure(req.getEmail());
            return ResponseEntity.status(401).body("Invalid credentials");
        });
    }
}
