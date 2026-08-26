package com.smartjobtracker.controller;

import com.smartjobtracker.dto.ReminderPreferencesDto;
import com.smartjobtracker.dto.ReminderScheduleRequest;
import com.smartjobtracker.model.Reminder;
import com.smartjobtracker.model.ReminderStatus;
import com.smartjobtracker.model.User;
import com.smartjobtracker.service.IntelligentReminderService;
import java.util.UUID;
import com.smartjobtracker.repository.ReminderRepository;
import com.smartjobtracker.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderRepository reminderRepository;
    private final UserRepository userRepository;
    private final IntelligentReminderService intelligentReminderService;

    public ReminderController(ReminderRepository reminderRepository, UserRepository userRepository,
                               IntelligentReminderService intelligentReminderService) {
        this.reminderRepository = reminderRepository;
        this.userRepository = userRepository;
        this.intelligentReminderService = intelligentReminderService;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        String email = auth.getName();
        User u = userRepository.findByEmail(email).orElse(null);
        return u == null ? null : u.getId();
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<Reminder>> upcoming() {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        // this user's still-pending reminders, soonest first
        List<Reminder> list = reminderRepository.findByUserIdAndStatusOrderByRemindAtAsc(uid, ReminderStatus.PENDING);
        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<Reminder> create(@RequestBody Reminder r) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        // always tie the reminder to the caller — never trust a userId from the request body
        r.setUserId(uid);
        if (r.getDedupeKey() == null || r.getDedupeKey().isBlank()) r.setDedupeKey("legacy-" + uid + "-" + UUID.randomUUID());
        Reminder saved = reminderRepository.save(r);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        Reminder existing = reminderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found"));
        if (!uid.equals(existing.getUserId())) {
            // 404 rather than 403 so we don't confirm to a caller that the id exists at all
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found");
        }
        reminderRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/schedule")
    public ResponseEntity<List<Reminder>> schedule(@Valid @RequestBody ReminderScheduleRequest request) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(intelligentReminderService.schedule(uid, request));
    }

    @GetMapping("/preferences")
    public ResponseEntity<ReminderPreferencesDto> preferences() {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(intelligentReminderService.getPreferences(uid));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ReminderPreferencesDto> savePreferences(@Valid @RequestBody ReminderPreferencesDto preferences) {
        Long uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(intelligentReminderService.savePreferences(uid, preferences));
    }
}
