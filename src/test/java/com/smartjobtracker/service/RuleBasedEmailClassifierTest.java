package com.smartjobtracker.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleBasedEmailClassifierTest {
    @Test
    void classifiesInterviewAndUsesOnlySignalsPresentInTheEmail() {
        EmailClassifier.Classification result = new RuleBasedEmailClassifier().classify(
                new EmailClassifier.EmailInput("recruiting@acme.test", "Interview invitation", "Please choose a time."));
        assertEquals("INTERVIEW_INVITATION", result.category());
        assertEquals("INTERVIEW", result.status());
        assertEquals(0.65, result.confidence());
        org.junit.jupiter.api.Assertions.assertNull(result.company());
    }
}