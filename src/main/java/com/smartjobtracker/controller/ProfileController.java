package com.smartjobtracker.controller;

import com.smartjobtracker.dto.CandidateProfileDto;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.CandidateProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Candidate profile API (Phase 1). Additive — new paths under {@code /api/profile},
 * authenticated by the existing JWT filter (covered by {@code anyRequest().authenticated()}).
 *
 * <ul>
 *   <li>{@code GET  /api/profile}          — current user's profile (404 if none yet)</li>
 *   <li>{@code POST /api/profile/extract}  — (re)build from a resume; {@code ?resumeId=} optional, defaults to latest</li>
 *   <li>{@code PUT  /api/profile}          — save manual edits (validated)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final CandidateProfileService profileService;
    private final UserRepository userRepository;

    public ProfileController(CandidateProfileService profileService, UserRepository userRepository) {
        this.profileService = profileService;
        this.userRepository = userRepository;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        User u = userRepository.findByEmail(auth.getName()).orElse(null);
        return u == null ? null : u.getId();
    }

    @GetMapping
    public ResponseEntity<?> getProfile() {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        Optional<CandidateProfileDto> profile = profileService.getProfile(uid);
        if (profile.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(profile.get());
    }

    @PostMapping("/extract")
    public ResponseEntity<?> extract(@RequestParam(value = "resumeId", required = false) Long resumeId) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(profileService.extractFromResume(uid, resumeId));
    }

    @PutMapping
    public ResponseEntity<?> save(@Valid @RequestBody CandidateProfileDto dto) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(profileService.saveEdits(uid, dto));
    }
}
