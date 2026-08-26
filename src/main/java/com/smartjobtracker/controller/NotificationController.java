package com.smartjobtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.NotificationPreferenceDto;
import com.smartjobtracker.dto.NotificationRequest;
import com.smartjobtracker.dto.NotificationVerificationDto;
import com.smartjobtracker.model.NotificationDelivery;
import com.smartjobtracker.model.NotificationPreference;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications; private final UserRepository users; private final ObjectMapper mapper;
    public NotificationController(NotificationService notifications, UserRepository users, ObjectMapper mapper) { this.notifications = notifications; this.users = users; this.mapper = mapper; }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreference> getPreferences() { Long id = userId(); if (id == null) return ResponseEntity.status(401).build(); return notifications.getPreference(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build()); }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreference> savePreferences(@Valid @RequestBody NotificationPreferenceDto request) { Long id = userId(); if (id == null) return ResponseEntity.status(401).build(); return ResponseEntity.ok(notifications.savePreference(id, request.getPhoneE164(), request.isWhatsappOptIn(), request.getConsentSource())); }

    @PostMapping("/whatsapp")
    public ResponseEntity<NotificationDelivery> send(@Valid @RequestBody NotificationRequest request) { Long id = userId(); if (id == null) return ResponseEntity.status(401).build(); return notifications.enqueueWhatsApp(id, request.getEventKey(), request.getMessage()).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(403).build()); }

    @PostMapping("/preferences/verify")
    public ResponseEntity<Void> startVerification() { Long id = userId(); if (id == null) return ResponseEntity.status(401).build(); notifications.startVerification(id); return ResponseEntity.accepted().build(); }

    @PostMapping("/preferences/confirm")
    public ResponseEntity<Void> confirmVerification(@Valid @RequestBody NotificationVerificationDto request) { Long id = userId(); if (id == null) return ResponseEntity.status(401).build(); notifications.verifyPhone(id, request.getCode()); return ResponseEntity.noContent().build(); }

    @GetMapping("/history")
    public ResponseEntity<List<NotificationDelivery>> history() { Long id = userId(); if (id == null) return ResponseEntity.status(401).build(); return ResponseEntity.ok(notifications.history(id)); }

    @GetMapping("/webhook")
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode, @RequestParam(name = "hub.verify_token", required = false) String token, @RequestParam(name = "hub.challenge", required = false) String challenge) { if ("subscribe".equals(mode) && notifications.verifyWebhookToken(token)) return ResponseEntity.ok(challenge); return ResponseEntity.status(403).body("Forbidden"); }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestHeader(name = "X-Hub-Signature-256", required = false) String signature, @RequestBody String body) { if (!notifications.validWebhookSignature(signature, body)) return ResponseEntity.status(403).build(); try { JsonNode payload = mapper.readTree(body); notifications.processWebhook(payload); return ResponseEntity.ok().build(); } catch (Exception ex) { return ResponseEntity.badRequest().build(); } }

    private Long userId() { Authentication auth = SecurityContextHolder.getContext().getAuthentication(); if (auth == null) return null; User user = users.findByEmail(auth.getName()).orElse(null); return user == null ? null : user.getId(); }
}