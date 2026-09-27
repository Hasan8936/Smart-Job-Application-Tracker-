package com.smartjobtracker.controller;

import com.smartjobtracker.dto.SupportDtos;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.SupportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** "Help & support" for signed-in users: their own tickets only. */
@RestController
@RequestMapping("/api/support/tickets")
public class SupportController {
    private final SupportService support;
    private final UserRepository users;

    public SupportController(SupportService support, UserRepository users) {
        this.support = support;
        this.users = users;
    }

    @PostMapping
    public ResponseEntity<SupportDtos.Ticket> create(@Valid @RequestBody SupportDtos.CreateTicket body) {
        Long uid = currentUserId();
        return uid == null ? ResponseEntity.status(401).build() : ResponseEntity.status(HttpStatus.CREATED).body(support.create(uid, body));
    }

    @GetMapping
    public ResponseEntity<List<SupportDtos.Ticket>> mine() {
        Long uid = currentUserId();
        return uid == null ? ResponseEntity.status(401).build() : ResponseEntity.ok(support.mine(uid));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportDtos.Ticket> one(@PathVariable Long id) {
        Long uid = currentUserId();
        return uid == null ? ResponseEntity.status(401).build() : ResponseEntity.ok(support.mine(uid, id));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<SupportDtos.Ticket> reply(@PathVariable Long id, @Valid @RequestBody SupportDtos.Reply body) {
        Long uid = currentUserId();
        return uid == null ? ResponseEntity.status(401).build() : ResponseEntity.ok(support.reply(uid, id, body));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return users.findByEmail(auth.getName()).map(User::getId).orElse(null);
    }
}
