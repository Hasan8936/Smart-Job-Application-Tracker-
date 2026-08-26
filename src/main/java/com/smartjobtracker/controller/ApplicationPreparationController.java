package com.smartjobtracker.controller;

import com.smartjobtracker.dto.ApplicationPreparationDtos;
import com.smartjobtracker.model.ApplicationSuggestionDecision;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.ResumeApplicationPreparationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/application-preparation")
public class ApplicationPreparationController {
    private final ResumeApplicationPreparationService service; private final UserRepository users;
    public ApplicationPreparationController(ResumeApplicationPreparationService service, UserRepository users) { this.service=service; this.users=users; }

    @GetMapping("/profile") public ResponseEntity<ApplicationPreparationDtos.Profile> profile() { Long id=userId(); if(id==null)return ResponseEntity.status(401).build(); ApplicationPreparationDtos.Profile profile=service.getProfile(id); return profile==null?ResponseEntity.noContent().build():ResponseEntity.ok(profile); }
    @PutMapping("/profile") public ResponseEntity<ApplicationPreparationDtos.Profile> saveProfile(@Valid @RequestBody ApplicationPreparationDtos.ProfileRequest request) { Long id=userId(); if(id==null)return ResponseEntity.status(401).build(); return ResponseEntity.ok(service.saveProfile(id,request)); }
    @PostMapping("/prepare") public ResponseEntity<ApplicationPreparationDtos.Preparation> prepare(@Valid @RequestBody ApplicationPreparationDtos.PrepareRequest request) { Long id=userId(); if(id==null)return ResponseEntity.status(401).build(); return ResponseEntity.ok(service.prepare(id,request)); }
    @GetMapping("/{id}") public ResponseEntity<ApplicationPreparationDtos.Preparation> get(@PathVariable Long id) { Long userId=userId(); if(userId==null)return ResponseEntity.status(401).build(); return ResponseEntity.ok(service.getPreparation(userId,id)); }
    @PatchMapping("/suggestions/{id}") public ResponseEntity<ApplicationPreparationDtos.Suggestion> decide(@PathVariable Long id,@Valid @RequestBody ApplicationPreparationDtos.DecisionRequest request) { Long userId=userId(); if(userId==null)return ResponseEntity.status(401).build(); return ResponseEntity.ok(service.decide(userId,id,request.decision())); }
    private Long userId() { Authentication auth=SecurityContextHolder.getContext().getAuthentication(); if(auth==null||auth.getName()==null)return null; User user=users.findByEmail(auth.getName()).orElse(null); return user==null?null:user.getId(); }
}