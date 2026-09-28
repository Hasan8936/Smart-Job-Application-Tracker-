package com.smartjobtracker.controller;

import com.smartjobtracker.dto.JobPreferencesDto;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.JobPreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Onboarding job preferences. Authenticated by the existing JWT filter ({@code anyRequest().authenticated()}).
 *
 * <ul>
 *   <li>{@code GET  /api/job-preferences}      — 200 with preferences (status SAVED or SKIPPED), 204 if never asked</li>
 *   <li>{@code PUT  /api/job-preferences}      — save (validated)</li>
 *   <li>{@code POST /api/job-preferences/skip} — dismiss the popup without answering</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/job-preferences")
public class JobPreferenceController {

    private final JobPreferenceService service;
    private final UserRepository userRepository;

    public JobPreferenceController(JobPreferenceService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        User u = userRepository.findByEmail(auth.getName()).orElse(null);
        return u == null ? null : u.getId();
    }

    @GetMapping
    public ResponseEntity<JobPreferencesDto> get() {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return service.get(uid).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public ResponseEntity<JobPreferencesDto> save(@Valid @RequestBody JobPreferencesDto body) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.save(uid, body));
    }

    @PostMapping("/skip")
    public ResponseEntity<Void> skip() {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        service.skip(uid);
        return ResponseEntity.noContent().build();
    }
}
