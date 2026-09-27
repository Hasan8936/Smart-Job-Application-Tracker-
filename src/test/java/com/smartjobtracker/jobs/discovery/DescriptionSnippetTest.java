package com.smartjobtracker.jobs.discovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DescriptionSnippetTest {

    @Test
    void startsAtTheRoleSectionInsteadOfCompanyBoilerplate() {
        String jd = "About Ramp\nRamp is building the smart infrastructure for finance teams across the world.\n\n"
                + "About the Role\nYou will design and build payment reconciliation services in Java and Spring Boot, "
                + "owning them end to end.\n\nWhat you'll do\n• Build APIs";
        String snippet = DescriptionSnippet.of(jd);
        assertTrue(snippet.startsWith("You will design and build payment reconciliation services"), snippet);
        assertFalse(snippet.contains("Ramp is building"));
    }

    @Test
    void usesResponsibilitiesHeadingWithColon() {
        String jd = "Acme is a leading fintech company headquartered in Bengaluru with 2,000 employees.\n\n"
                + "Key Responsibilities:\n• Develop microservices in Go\n• Maintain CI/CD pipelines on AWS for production workloads";
        assertTrue(DescriptionSnippet.of(jd).startsWith("• Develop microservices in Go"));
    }

    @Test
    void skipsAboutCompanyParagraphWhenThereIsNoHeading() {
        String jd = "About Acme: we are a fast-growing startup.\n\n"
                + "We are hiring a backend engineer to build scalable REST APIs with Java, Spring Boot and PostgreSQL.";
        assertTrue(DescriptionSnippet.of(jd).startsWith("We are hiring a backend engineer"));
    }

    @Test
    void keepsPlainDescriptionsAndTruncatesOnAWordBoundary() {
        String jd = "Build and scale core services used by millions of users. ".repeat(10);
        String snippet = DescriptionSnippet.of(jd);
        assertTrue(snippet.startsWith("Build and scale core services"));
        assertTrue(snippet.length() <= DescriptionSnippet.DEFAULT_LENGTH + 1);
        assertTrue(snippet.endsWith("…"));
        assertFalse(snippet.contains("  "));
        assertEquals("Short JD.", DescriptionSnippet.of("Short JD."));
        assertNull(DescriptionSnippet.of("   "));
        assertNull(DescriptionSnippet.of(null));
    }
}
