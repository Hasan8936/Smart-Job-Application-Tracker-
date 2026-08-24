package com.smartjobtracker.service;

import com.smartjobtracker.model.PasswordResetToken;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.PasswordResetTokenRepository;
import com.smartjobtracker.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class PasswordResetService {
    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(PasswordResetTokenRepository tokenRepository, UserRepository userRepository,
                                EmailService emailService, PasswordEncoder passwordEncoder) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void requestReset(String email) {
        var user = userRepository.findByEmail(email);
        if (user.isEmpty()) return;

        tokenRepository.invalidateAllForEmail(email);
        String rawToken = generateRawToken();
        tokenRepository.save(new PasswordResetToken(sha256Hex(rawToken), email,
                LocalDateTime.now().plusMinutes(30)));
        emailService.sendPasswordResetEmail(email, rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link"));
        if (token.isUsed() || token.isExpired()) {
            throw new IllegalArgumentException("Invalid or expired reset link");
        }
        User user = userRepository.findByEmail(token.getUserEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsed(true);
        userRepository.save(user);
        tokenRepository.save(token);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create reset token", exception);
        }
    }
}