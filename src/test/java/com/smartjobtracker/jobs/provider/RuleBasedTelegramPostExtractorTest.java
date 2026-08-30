package com.smartjobtracker.jobs.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuleBasedTelegramPostExtractorTest {

    private final RuleBasedTelegramPostExtractor extractor = new RuleBasedTelegramPostExtractor();

    @Test
    void extractsClearlyLabeledFields() {
        String text = "Hiring for: Backend Engineer\nRole: Backend Engineer\nCompany: Acme Corp\n"
                + "Location: Remote\nFull-time position\nApply: https://forms.gle/abc123";
        TelegramPostExtractor.Extraction result = extractor.extract(new TelegramPostExtractor.Input(text, List.of()));

        assertEquals("Backend Engineer", result.title());
        assertEquals("Acme Corp", result.company());
        assertEquals("Remote", result.location());
        assertEquals("full-time", result.employmentType().toLowerCase());
        assertEquals("https://forms.gle/abc123", result.applyUrl());
        assertEquals("rules", result.provider());
    }

    @Test
    void leavesFieldsNullRatherThanGuessingWhenNoLabelsPresent() {
        String text = "Big things coming this week, stay tuned everyone!";
        TelegramPostExtractor.Extraction result = extractor.extract(new TelegramPostExtractor.Input(text, List.of()));

        assertNull(result.title());
        assertNull(result.company());
        assertNull(result.location());
        assertNull(result.applyUrl());
    }

    @Test
    void prefersAnHrefLinkOverABareUrlInText() {
        String text = "Role: Data Analyst\nSee https://example.com/mentioned-in-passing for details";
        TelegramPostExtractor.Extraction result = extractor.extract(
                new TelegramPostExtractor.Input(text, List.of("https://forms.gle/real-application-link")));

        assertEquals("https://forms.gle/real-application-link", result.applyUrl());
    }

    @Test
    void alwaysReturnsALowFixedConfidence() {
        TelegramPostExtractor.Extraction result = extractor.extract(new TelegramPostExtractor.Input("Role: X", List.of()));
        assertEquals(0.35, result.confidence());
    }
}
