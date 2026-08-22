package com.smartjobtracker.controller;

import com.smartjobtracker.dto.MatchRequest;
import com.smartjobtracker.dto.MatchResponse;
import com.smartjobtracker.service.KeywordMatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/match")
public class MatchController {

    private final KeywordMatchService keywordMatchService;

    public MatchController(KeywordMatchService keywordMatchService) {
        this.keywordMatchService = keywordMatchService;
    }

    @PostMapping("/score")
    public ResponseEntity<MatchResponse> score(@RequestBody MatchRequest req) {
        MatchResponse res = keywordMatchService.score(req.getResumeId(), req.getJobDescriptionText());
        return ResponseEntity.ok(res);
    }
}
