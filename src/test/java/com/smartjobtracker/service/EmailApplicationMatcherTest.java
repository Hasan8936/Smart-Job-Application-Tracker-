package com.smartjobtracker.service;

import com.smartjobtracker.model.JobApplication;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EmailApplicationMatcherTest {
    private final EmailApplicationMatcher matcher = new EmailApplicationMatcher();

    @Test
    void prefersApplicationReferenceOverCompanyAndRole() {
        JobApplication referenced = application(10L, "Other Co", "Other Role");
        JobApplication companyMatch = application(11L, "Acme", "Engineer");
        EmailClassifier.Classification classification = classification("Acme", "Engineer", "10", 0.99);
        EmailApplicationMatcher.MatchResult result = matcher.match(List.of(referenced, companyMatch), classification, 0.80);
        assertEquals(10L, result.application().getId());
        assertEquals("APPLICATION_REFERENCE", result.method());
    }

    @Test
    void normalizesCompanyAndRoleButRejectsIncorrectCompany() {
        JobApplication application = application(12L, "Acme, Inc.", "Senior Engineer");
        EmailApplicationMatcher.MatchResult match = matcher.match(List.of(application), classification(" acme inc ", "senior-engineer", null, 0.90), 0.80);
        assertNotNull(match);
        assertEquals("NORMALIZED_COMPANY_ROLE", match.method());
        assertNull(matcher.match(List.of(application), classification("Wrong Company", "Senior Engineer", null, 0.99), 0.80));
    }

    @Test
    void ambiguousCompanyOnlyMatchRequiresManualReview() {
        JobApplication first = application(13L, "Acme", "Engineer");
        JobApplication second = application(14L, "Acme", "Analyst");
        assertNull(matcher.match(List.of(first, second), classification("Acme", "Unknown", null, 0.99), 0.80));
    }

    private JobApplication application(Long id, String company, String role) {
        JobApplication application = new JobApplication(); application.setId(id); application.setCompanyName(company); application.setRoleTitle(role); return application;
    }
    private EmailClassifier.Classification classification(String company, String role, String reference, double confidence) {
        return new EmailClassifier.Classification("APPLICATION_STATUS_UPDATE", company, role, "INTERVIEW", null, null, null, null, reference, confidence, "test");
    }
}
