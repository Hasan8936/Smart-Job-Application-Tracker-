package com.smartjobtracker.controller;

import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.JobActionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/jobs")
public class JobActionController {
    private final JobActionService service; private final UserRepository users;
    public JobActionController(JobActionService service, UserRepository users) { this.service=service; this.users=users; }
    @PostMapping("/{id}/save") public SavedJob save(@PathVariable Long id) { return service.setState(userId(), id, "SAVED"); }
    @PostMapping("/{id}/bookmark") public SavedJob bookmark(@PathVariable Long id) { return service.setState(userId(), id, "BOOKMARKED"); }
    @PostMapping("/{id}/applied") public SavedJob applied(@PathVariable Long id) { return service.markApplied(userId(), id); }
    @PostMapping("/{id}/documents/{type}") public GeneratedDocument generate(@PathVariable Long id, @PathVariable String type) { return service.generate(userId(), id, type.toUpperCase()); }
    @GetMapping("/{id}/documents") public List<GeneratedDocument> documents(@PathVariable Long id) { return service.listDocuments(userId(), id); }
    @PutMapping("/documents/{documentId}") public GeneratedDocument update(@PathVariable Long documentId, @RequestBody DocumentRequest request) { return service.updateDocument(userId(), documentId, request.content()); }
    private Long userId() { Authentication a=SecurityContextHolder.getContext().getAuthentication(); if(a==null) throw new IllegalStateException("Unauthenticated"); return users.findByEmail(a.getName()).map(User::getId).orElseThrow(() -> new IllegalStateException("User not found")); }
    public record DocumentRequest(String content) {}
}