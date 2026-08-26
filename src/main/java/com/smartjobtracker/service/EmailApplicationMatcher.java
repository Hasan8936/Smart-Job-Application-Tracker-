package com.smartjobtracker.service;

import com.smartjobtracker.model.JobApplication;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;

@Component
public class EmailApplicationMatcher {
    public MatchResult match(List<JobApplication> applications, EmailClassifier.Classification classification,
                             double minimumConfidence) {
        if (classification.applicationReference() != null) {
            try {
                Long id = Long.valueOf(classification.applicationReference());
                return applications.stream().filter(application -> id.equals(application.getId()))
                        .findFirst().map(application -> new MatchResult(application, "APPLICATION_REFERENCE", 1.0)).orElse(null);
            } catch (NumberFormatException ignored) { }
        }
        if (classification.company() == null || classification.jobTitle() == null) return null;
        String company = classification.company().trim();
        String role = classification.jobTitle().trim();
        var exact = applications.stream().filter(application -> company.equals(application.getCompanyName())
                && role.equals(application.getRoleTitle())).findFirst();
        if (exact.isPresent()) return new MatchResult(exact.get(), "EXACT_COMPANY_ROLE", classification.confidence());
        String normalizedCompany = normalize(company); String normalizedRole = normalize(role);
        var normalized = applications.stream().filter(application -> normalizedCompany.equals(normalize(application.getCompanyName()))
                && normalizedRole.equals(normalize(application.getRoleTitle()))).findFirst();
        if (normalized.isPresent()) return new MatchResult(normalized.get(), "NORMALIZED_COMPANY_ROLE", classification.confidence());
        if (classification.confidence() >= minimumConfidence) {
            var highConfidence = applications.stream().filter(application -> normalizedCompany.equals(normalize(application.getCompanyName()))
                && normalizedRole.equals(normalize(application.getRoleTitle()))).toList();
            if (highConfidence.size() == 1) return new MatchResult(highConfidence.get(0), "HIGH_CONFIDENCE_AI", classification.confidence());
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "").trim();
    }

    public record MatchResult(JobApplication application, String method, double confidence) { }
}