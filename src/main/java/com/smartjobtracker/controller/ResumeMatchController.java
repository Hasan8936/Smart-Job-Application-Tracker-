package com.smartjobtracker.controller;

import com.smartjobtracker.dto.DeepMatchDtos.DeepMatchRequest;
import com.smartjobtracker.dto.DeepMatchDtos.DeepMatchResult;
import com.smartjobtracker.service.ResumeDeepMatchService;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/api/resume")
public class ResumeMatchController {

    private final ResumeDeepMatchService deepMatchService;
    private final UserRepository users;

    public ResumeMatchController(ResumeDeepMatchService deepMatchService, UserRepository users) {
        this.deepMatchService = deepMatchService;
        this.users = users;
    }

    @PostMapping("/deep-match")
    public ResponseEntity<DeepMatchResult> deepMatch(@Valid @RequestBody DeepMatchRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = users.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(deepMatchService.analyze(user.getId(), request));
    }
}