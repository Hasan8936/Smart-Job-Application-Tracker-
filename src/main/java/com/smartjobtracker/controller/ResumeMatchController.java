package com.smartjobtracker.controller;

import com.smartjobtracker.dto.DeepMatchDtos.DeepMatchRequest;
import com.smartjobtracker.dto.DeepMatchDtos.DeepMatchResult;
import com.smartjobtracker.service.ResumeDeepMatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resume")
public class ResumeMatchController {

    private final ResumeDeepMatchService deepMatchService;

    public ResumeMatchController(ResumeDeepMatchService deepMatchService) {
        this.deepMatchService = deepMatchService;
    }

    @PostMapping("/deep-match")
    public ResponseEntity<DeepMatchResult> deepMatch(@Valid @RequestBody DeepMatchRequest request) {
        return ResponseEntity.ok(deepMatchService.analyze(request.resumeText(), request.jobDescription()));
    }
}