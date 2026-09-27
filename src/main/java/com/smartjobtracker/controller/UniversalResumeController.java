package com.smartjobtracker.controller;

import com.smartjobtracker.dto.ResumeBuilderDto;
import com.smartjobtracker.dto.UniversalResumeDtos;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.UniversalResumeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resume")
public class UniversalResumeController {
    private final UniversalResumeService service;
    private final UserRepository users;

    public UniversalResumeController(UniversalResumeService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    /** The user's universal resume, or 404 when they haven't created one. */
    @GetMapping("/universal")
    public ResponseEntity<UniversalResumeDtos.Response> get() {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return service.get(uid).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Creates or replaces the universal resume; its matching text is regenerated. */
    @PutMapping("/universal")
    public ResponseEntity<UniversalResumeDtos.Response> save(@Valid @RequestBody ResumeBuilderDto dto) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.save(uid, dto));
    }

    /** Starts the universal resume from one of the user's uploaded resumes. */
    @PostMapping("/universal/from-resume")
    public ResponseEntity<UniversalResumeDtos.Response> fromResume(@RequestParam Long resumeId,
                                                                   @RequestParam(defaultValue = "false") boolean overwrite) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.initFromResume(uid, resumeId, overwrite));
    }

    /** Which resume matching uses by default: universal, else the newest upload. */
    @GetMapping("/matching")
    public ResponseEntity<UniversalResumeDtos.MatchingResume> matching() {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.matchingResume(uid));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return users.findByEmail(auth.getName()).map(User::getId).orElse(null);
    }
}
