package com.smartjobtracker.controller;

import com.smartjobtracker.dto.InterviewPrepDtos;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.InterviewPrepService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/interview-prep")
public class InterviewPrepController {
    private final InterviewPrepService service;
    private final UserRepository users;

    public InterviewPrepController(InterviewPrepService service, UserRepository users) { this.service = service; this.users = users; }

    @PostMapping("/generate")
    public ResponseEntity<InterviewPrepDtos.Session> generate(@Valid @RequestBody InterviewPrepDtos.GenerateRequest request) {
        Long userId = userId(); if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.generate(userId, request));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<InterviewPrepDtos.SessionSummary>> sessions() {
        Long userId = userId(); if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.listSessions(userId));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<InterviewPrepDtos.Session> session(@PathVariable Long id) {
        Long userId = userId(); if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.getSession(userId, id));
    }

    @GetMapping("/sessions/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        Long userId = userId(); if (userId == null) return ResponseEntity.status(401).build();
        byte[] body = service.exportMarkdown(userId, id).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("interview-prep-" + id + ".md").build().toString())
                .body(body);
    }

    private Long userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        User user = users.findByEmail(auth.getName()).orElse(null);
        return user == null ? null : user.getId();
    }
}
