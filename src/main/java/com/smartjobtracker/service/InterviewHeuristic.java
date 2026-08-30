package com.smartjobtracker.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Flags text that LOOKS interview-related. This is a heuristic for surfacing candidates for
 * human review, not a classifier of ground truth — a match here means "worth showing the user",
 * never "confirmed interview". Callers must not change any application status based solely on
 * a match here; only an explicit user confirmation may do that (see InterviewCandidate flow).
 */
@Component
public class InterviewHeuristic {

    // Deliberately not exhaustive — broad enough to catch common phrasing without pretending
    // to be a complete list. False positives are expected and are the reviewer's job to filter.
    private static final List<String> SIGNAL_PHRASES = List.of(
            "interview", "phone screen", "screening call", "technical round", "technical interview",
            "onsite", "on-site", "schedule a call", "schedule a chat", "hiring manager",
            "next steps", "chat with the team", "recruiter call", "coding interview",
            "system design interview", "behavioral interview", "final round"
    );

    public boolean looksLikeInterview(String... textParts) {
        String combined = String.join(" ", textParts).toLowerCase(Locale.ROOT);
        return SIGNAL_PHRASES.stream().anyMatch(combined::contains);
    }
}
