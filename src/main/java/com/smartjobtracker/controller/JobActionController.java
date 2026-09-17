package com.smartjobtracker.controller;

import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.JobActionService;
import com.smartjobtracker.service.SkyvernService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/jobs")
public class JobActionController {
    private final JobActionService service;
    private final UserRepository users;
    private final SkyvernService skyvernService;

    public JobActionController(JobActionService service, UserRepository users, SkyvernService skyvernService) {
        this.service = service; this.users = users; this.skyvernService = skyvernService;
    }

    @PostMapping("/{id}/save") public SavedJob save(@PathVariable Long id) { return service.setState(userId(), id, "SAVED"); }
    @PostMapping("/{id}/bookmark") public SavedJob bookmark(@PathVariable Long id) { return service.setState(userId(), id, "BOOKMARKED"); }
    @PostMapping("/{id}/applied") public SavedJob applied(@PathVariable Long id) { return service.markApplied(userId(), id); }
    @PostMapping("/{id}/documents/{type}") public GeneratedDocument generate(@PathVariable Long id, @PathVariable String type) { return service.generate(userId(), id, type.toUpperCase()); }
    @GetMapping("/{id}/documents") public List<GeneratedDocument> documents(@PathVariable Long id) { return service.listDocuments(userId(), id); }
    @PutMapping("/documents/{documentId}") public GeneratedDocument update(@PathVariable Long documentId, @RequestBody DocumentRequest request) { return service.updateDocument(userId(), documentId, request.content()); }

    @PostMapping("/{id}/auto-apply")
    public ResponseEntity<Map<String, Object>> autoApply(@PathVariable Long id) {
        if (!skyvernService.isConfigured()) {
            return ResponseEntity.status(503).body(Map.of(
                "error", "Auto-apply is not configured on this server. Set SKYVERN_API_URL and SKYVERN_API_KEY."
            ));
        }
        String taskId = skyvernService.autoApply(userId(), id);
        return ResponseEntity.ok(Map.of("taskId", taskId, "status", "PENDING"));
    }

    @GetMapping("/auto-apply/{taskId}/status")
    public ResponseEntity<Map<String, String>> autoApplyStatus(@PathVariable String taskId) {
        String status = skyvernService.getTaskStatus(taskId);
        return ResponseEntity.ok(Map.of("taskId", taskId, "status", status));
    }

    private Long userId() { Authentication a=SecurityContextHolder.getContext().getAuthentication(); if(a==null) throw new IllegalStateException("Unauthenticated"); return users.findByEmail(a.getName()).map(User::getId).orElseThrow(() -> new IllegalStateException("User not found")); }
    public record DocumentRequest(String content) {}
}