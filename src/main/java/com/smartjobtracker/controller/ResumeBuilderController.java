package com.smartjobtracker.controller;

import com.smartjobtracker.dto.ResumeBuilderDto;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.ResumeBuilderService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resume/build")
public class ResumeBuilderController {

    private final ResumeBuilderService builderService;
    private final UserRepository userRepository;

    public ResumeBuilderController(ResumeBuilderService builderService, UserRepository userRepository) {
        this.builderService = builderService;
        this.userRepository = userRepository;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        User u = userRepository.findByEmail(auth.getName()).orElse(null);
        return u == null ? null : u.getId();
    }

    /** Returns profile-based pre-fill data so the form can start populated. */
    @GetMapping("/prefill")
    public ResponseEntity<?> prefill() {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(builderService.prefill(uid));
    }

    /** Generates PDF, saves as a Resume in the user's account, returns PDF bytes. */
    @PostMapping("/export")
    public ResponseEntity<?> export(@RequestBody ResumeBuilderDto dto) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        var result = builderService.export(uid, dto);
        byte[] pdf = (byte[]) result.get("pdf");
        String fileName = (String) result.get("fileName");
        Long resumeId = (Long) result.get("resumeId");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header("X-Resume-Id", String.valueOf(resumeId))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /** Renders a PDF preview without saving (called on Step 3 to show preview). */
    @PostMapping("/preview")
    public ResponseEntity<byte[]> preview(@RequestBody ResumeBuilderDto dto) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        byte[] pdf = builderService.renderOnly(dto);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
