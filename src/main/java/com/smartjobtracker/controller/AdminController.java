package com.smartjobtracker.controller;

import com.smartjobtracker.dto.AdminDtos;
import com.smartjobtracker.dto.SupportDtos;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/** Super-admin console. /api/admin/** is ADMIN-only in SecurityConfig, and AdminService re-checks with @PreAuthorize. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;
    private final UserRepository users;

    public AdminController(AdminService admin, UserRepository users) {
        this.admin = admin;
        this.users = users;
    }

    @GetMapping("/users")
    public Page<AdminDtos.UserSummary> users(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
                                             @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return admin.searchUsers(q, from, to, page, size);
    }

    @GetMapping("/users/{id}")
    public AdminDtos.UserDetail user(@PathVariable Long id) { return admin.userDetail(adminId(), id); }

    @PostMapping("/users/{id}/resumes/{resumeId}/view")
    public AdminDtos.ResumeText viewResume(@PathVariable Long id, @PathVariable Long resumeId) {
        return admin.viewResume(adminId(), id, resumeId);
    }

    @PostMapping("/users/{id}/flags")
    public AdminDtos.Flag flag(@PathVariable Long id, @Valid @RequestBody AdminDtos.NewFlag body) {
        return admin.addFlag(adminId(), id, body);
    }

    @PostMapping("/flags/{flagId}/resolve")
    public AdminDtos.Flag resolve(@PathVariable Long flagId) { return admin.resolveFlag(adminId(), flagId); }

    @PostMapping("/users/{id}/suspend")
    public AdminDtos.UserSummary suspend(@PathVariable Long id, @Valid @RequestBody(required = false) AdminDtos.Suspend body) {
        return admin.setSuspended(adminId(), id, true, body == null ? null : body.reason());
    }

    @PostMapping("/users/{id}/unsuspend")
    public AdminDtos.UserSummary unsuspend(@PathVariable Long id) { return admin.setSuspended(adminId(), id, false, null); }

    @GetMapping("/tickets")
    public Page<SupportDtos.Ticket> tickets(@RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return admin.tickets(status, page, size);
    }

    @GetMapping("/tickets/{id}")
    public SupportDtos.Ticket ticket(@PathVariable Long id) { return admin.ticket(id); }

    @PostMapping("/tickets/{id}/messages")
    public SupportDtos.Ticket reply(@PathVariable Long id, @Valid @RequestBody SupportDtos.Reply body) {
        return admin.reply(adminId(), id, body);
    }

    @PatchMapping("/tickets/{id}")
    public SupportDtos.Ticket update(@PathVariable Long id, @Valid @RequestBody SupportDtos.AdminUpdate body) {
        return admin.update(adminId(), id, body);
    }

    @GetMapping("/audit")
    public Page<AdminDtos.AuditEntry> audit(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return admin.auditLog(page, size);
    }

    private Long adminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return users.findByEmail(auth.getName()).map(User::getId)
                .orElseThrow(() -> new IllegalStateException("Authenticated admin not found"));
    }

}
