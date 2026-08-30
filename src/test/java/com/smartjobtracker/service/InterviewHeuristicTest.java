package com.smartjobtracker.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewHeuristicTest {

    private final InterviewHeuristic heuristic = new InterviewHeuristic();

    @Test
    void flagsClearInterviewLanguage() {
        assertTrue(heuristic.looksLikeInterview("Technical interview with the platform team", ""));
        assertTrue(heuristic.looksLikeInterview("Quick call to discuss next steps", "Let's schedule a call for Tuesday"));
        assertTrue(heuristic.looksLikeInterview("Onsite - Backend Engineer", ""));
        assertTrue(heuristic.looksLikeInterview("", "Please join us for a phone screen with our hiring manager"));
    }

    @Test
    void doesNotFlagUnrelatedEvents() {
        assertFalse(heuristic.looksLikeInterview("Dentist appointment", ""));
        assertFalse(heuristic.looksLikeInterview("Team standup", "Daily sync at 9am"));
        assertFalse(heuristic.looksLikeInterview("Birthday dinner", "Celebrating with friends"));
    }

    @Test
    void isCaseInsensitiveAndHandlesNullishEmptyInputs() {
        assertTrue(heuristic.looksLikeInterview("FINAL ROUND with the team", ""));
        assertFalse(heuristic.looksLikeInterview("", ""));
    }

    @Test
    void knownFalsePositiveRisk_genericCallMentionWithoutInterviewContext() {
        // Documents a real limitation: "next steps" is broad enough to also match non-interview
        // planning emails. This is expected — the heuristic surfaces candidates for human review,
        // it does not claim to be precise. Recorded here so the trade-off is visible in test output,
        // not silently assumed.
        assertTrue(heuristic.looksLikeInterview("Next steps for the community fundraiser", ""));
    }
}
