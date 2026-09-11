package com.smartjobtracker.controller;

import com.smartjobtracker.dto.MatchRequest;
import com.smartjobtracker.dto.MatchResponse;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.KeywordMatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/match")
public class MatchController {

    private final KeywordMatchService keywordMatchService;
    private final UserRepository userRepository;

    public MatchController(KeywordMatchService keywordMatchService, UserRepository userRepository) {
        this.keywordMatchService = keywordMatchService;
        this.userRepository = userRepository;
    }

    @PostMapping("/score")
    public ResponseEntity<MatchResponse> score(@Valid @RequestBody MatchRequest req) {
        Long userId = currentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        MatchResponse res = keywordMatchService.score(req.getResumeId(), userId, req.getJobDescriptionText());
        return ResponseEntity.ok(res);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        return userRepository.findByEmail(auth.getName()).map(User::getId).orElse(null);
    }
}
