package com.smartjobtracker.controller;

import com.smartjobtracker.dto.ResumeTailoringDtos;
import com.smartjobtracker.model.TailoringSuggestionDecision;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.ResumeTailoringService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/resume-tailoring")
public class ResumeTailoringController {
    private final ResumeTailoringService service;
    private final UserRepository users;

    public ResumeTailoringController(ResumeTailoringService service, UserRepository users) { this.service = service; this.users = users; }

    @PostMapping("/analyze")
    public ResponseEntity<ResumeTailoringDtos.Analysis> analyze(@Valid @RequestBody ResumeTailoringDtos.AnalyzeRequest request) {
        Long userId = userId(); if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.analyze(userId, request));
    }

    @PatchMapping("/suggestions/{suggestionId}")
    public ResponseEntity<ResumeTailoringDtos.Suggestion> decide(@PathVariable Long suggestionId, @Valid @RequestBody ResumeTailoringDtos.DecisionRequest request) {
        Long userId = userId(); if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.decide(userId, suggestionId, request.decision()));
    }

    @PostMapping("/sessions/{sessionId}/versions")
    public ResponseEntity<ResumeTailoringDtos.Version> createVersion(@PathVariable Long sessionId) {
        Long userId = userId(); if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.createVersion(userId, sessionId));
    }

    @GetMapping("/versions")
    public ResponseEntity<List<ResumeTailoringDtos.Version>> versions() {
        Long userId = userId(); if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.versions(userId));
    }

    private Long userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        User user = users.findByEmail(auth.getName()).orElse(null);
        return user == null ? null : user.getId();
    }
}