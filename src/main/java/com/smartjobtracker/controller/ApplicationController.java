package com.smartjobtracker.controller;

import com.smartjobtracker.dto.ApplicationRequest;
import com.smartjobtracker.dto.StatusPatchRequest;
import com.smartjobtracker.model.ApplicationStatusHistory;
import com.smartjobtracker.model.JobApplication;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.JobApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final JobApplicationService service;
    private final UserRepository userRepository;

    public ApplicationController(JobApplicationService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        String email = auth.getName();
        User u = userRepository.findByEmail(email).orElse(null);
        return u == null ? null : u.getId();
    }

    @GetMapping
    public ResponseEntity<List<JobApplication>> list() {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.listByUser(uid));
    }

    @PostMapping
    public ResponseEntity<JobApplication> create(@RequestBody ApplicationRequest req) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        JobApplication a = new JobApplication();
        a.setUserId(uid);
        a.setCompanyName(req.getCompanyName());
        a.setRoleTitle(req.getRoleTitle());
        a.setJobDescription(req.getJobDescription());
        a.setStatus(req.getStatus());
        a.setAppliedDate(req.getAppliedDate());
        JobApplication saved = service.create(a);
        return ResponseEntity.created(URI.create("/api/applications/" + saved.getId())).body(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobApplication> get(@PathVariable Long id) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return service.get(id, uid).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobApplication> update(@PathVariable Long id, @RequestBody ApplicationRequest req) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        JobApplication update = new JobApplication();
        update.setCompanyName(req.getCompanyName());
        update.setRoleTitle(req.getRoleTitle());
        update.setJobDescription(req.getJobDescription());
        update.setAppliedDate(req.getAppliedDate());
        update.setStatus(req.getStatus());
        JobApplication saved = service.update(id, uid, update);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        service.delete(id, uid);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationStatusHistory> patchStatus(@PathVariable Long id, @RequestBody StatusPatchRequest req) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        ApplicationStatusHistory h = service.changeStatus(id, uid, req.getStatus(), req.getRemark());
        return ResponseEntity.ok(h);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<ApplicationStatusHistory>> history(@PathVariable Long id) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(service.getHistory(id, uid));
    }
}
