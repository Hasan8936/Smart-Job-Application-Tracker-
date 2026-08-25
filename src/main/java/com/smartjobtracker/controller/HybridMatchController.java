package com.smartjobtracker.controller;

import com.smartjobtracker.dto.HybridMatchDtos;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.jobs.match.HybridMatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/match")
public class HybridMatchController {
    private final HybridMatchService service; private final UserRepository users;
    public HybridMatchController(HybridMatchService service, UserRepository users) { this.service = service; this.users = users; }
    @PostMapping("/hybrid-score")
    public ResponseEntity<HybridMatchDtos.Response> score(@Valid @RequestBody HybridMatchDtos.Request request) {
        Long userId = currentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.match(userId, request));
    }
    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) return null;
        return users.findByEmail(authentication.getName()).map(User::getId).orElse(null);
    }
}