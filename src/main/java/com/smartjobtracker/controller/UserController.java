package com.smartjobtracker.controller;

import com.smartjobtracker.dto.UserProfile;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final com.smartjobtracker.repository.UserPhotoRepository photos;

    public UserController(UserRepository userRepository, com.smartjobtracker.repository.UserPhotoRepository photos) {
        this.userRepository = userRepository;
        this.photos = photos;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfile> me() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User u = userRepository.findByEmail(email).orElse(null);
        if (u == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new UserProfile(u.getId(), u.getName(), u.getEmail(),
                u.getRole(), u.isPasswordSet(), photos.existsById(u.getId())));
    }
}
