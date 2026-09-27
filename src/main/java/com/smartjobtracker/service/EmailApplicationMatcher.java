package com.smartjobtracker.service;

import com.smartjobtracker.model.JobApplication;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;

@Component
public class EmailApplicationMatcher {
    public MatchResult match(List<JobApplication> applications, EmailClassifier.Classification classification,
                             double minimumConfidence) {
        return match(applications, classification, null, minimumConfidence);
    }

    /**
     * Same as {@link #match(List, EmailClassifier.Classification, double)}, then two company-level fallbacks that
     * only succeed when exactly one application fits (ambiguity always goes to manual review):
     * the classifier's company alone, then an application's company name appearing in the email text
     * (sender, subject, snippet). The rule-based classifier extracts no company, so the second fallback is
     * what lets "Thanks for applying to Stripe" find the Stripe application.
     */
    public MatchResult match(List<JobApplication> applications, EmailClassifier.Classification classification,
                             String emailText, double minimumConfidence) {
        MatchResult precise = matchCompanyAndRole(applications, classification, minimumConfidence);
        if (precise != null) return precise;
        if (classification.applicationReference() != null && isNumeric(classification.applicationReference())) return null;
        if (classification.company() != null && !classification.company().isBlank()) {
            String company = normalize(classification.company());
            List<JobApplication> atCompany = applications.stream().filter(a -> company.equals(normalize(a.getCompanyName()))).toList();
            if (atCompany.size() == 1) return new MatchResult(atCompany.get(0), "COMPANY_ONLY", classification.confidence());
            if (atCompany.size() > 1) return null;
        }
        if (emailText == null || emailText.isBlank()) return null;
        String text = normalize(emailText);
        List<JobApplication> mentioned = applications.stream().filter(a -> {
            String company = normalize(a.getCompanyName());
            return company.length() >= MIN_COMPANY_LENGTH && text.contains(company);
        }).toList();
        if (mentioned.isEmpty()) return null;
        // Several applications at the same company are still ambiguous; different companies mentioned too.
        return mentioned.size() == 1 ? new MatchResult(mentioned.get(0), "COMPANY_IN_EMAIL", classification.confidence()) : null;
    }

    /** Shorter names ("X", "HP") would match inside unrelated words once punctuation is stripped. */
    private static final int MIN_COMPANY_LENGTH = 3;

    private static boolean isNumeric(String value) { return value.trim().matches("\\d+"); }

    private MatchResult matchCompanyAndRole(List<JobApplication> applications, EmailClassifier.Classification classification,
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