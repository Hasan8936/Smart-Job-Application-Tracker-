package com.smartjobtracker.controller;

import com.smartjobtracker.dto.AuthRequest;
import com.smartjobtracker.dto.AuthResponse;
import com.smartjobtracker.dto.RegisterRequest;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.security.JwtUtil;
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

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                          com.smartjobtracker.service.SuperAdminBootstrap superAdmin) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.superAdmin = superAdmin;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (userRepository.findByEmail(req.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Email already in use");
        }
        User u = new User();
        u.setName(req.getName());
        u.setEmail(req.getEmail());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        userRepository.save(u);
        return ResponseEntity.created(URI.create("/api/auth/register")).build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req) {
        return userRepository.findByEmail(req.getEmail()).map(u -> {
            if (!passwordEncoder.matches(req.getPassword(), u.getPasswordHash())) {
                return ResponseEntity.status(401).body("Invalid credentials");
            }
            if (u.isSuspended()) {
                return ResponseEntity.status(403).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body(com.smartjobtracker.security.JwtFilter.SUSPENDED_BODY);
            }
            superAdmin.promoteIfConfigured(u);
            u.setLastLoginAt(java.time.OffsetDateTime.now());
            userRepository.save(u);
            String token = jwtUtil.generateToken(u.getEmail());
            return ResponseEntity.ok(new AuthResponse(token));
        }).orElse(ResponseEntity.status(401).body("Invalid credentials"));
    }
}
