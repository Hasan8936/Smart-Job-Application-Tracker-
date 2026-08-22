package com.smartjobtracker.controller;

import com.smartjobtracker.model.Resume;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.ResumeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumeService resumeService;
    private final UserRepository userRepository;

    public ResumeController(ResumeService resumeService, UserRepository userRepository) {
        this.resumeService = resumeService;
        this.userRepository = userRepository;
    }

    private Long currentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User u = userRepository.findByEmail(email).orElse(null);
        return u == null ? null : u.getId();
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws Exception {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        Resume r = resumeService.upload(uid, file);
        return ResponseEntity.ok(r);
    }

    @GetMapping("/me")
    public ResponseEntity<List<Resume>> myResumes() {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(resumeService.listByUser(uid));
    }
}
