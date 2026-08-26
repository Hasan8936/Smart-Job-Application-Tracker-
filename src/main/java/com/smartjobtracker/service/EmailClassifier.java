package com.smartjobtracker.service;

import java.util.Set;

public interface EmailClassifier {
    Classification classify(EmailInput input);

    record EmailInput(String from, String subject, String snippet) {}
    record Classification(String category, String company, String jobTitle, String status,
                          String interviewDate, String interviewTime, String deadline,
                          String actionRequired, String applicationReference, double confidence, String provider) {
        public static final Set<String> CATEGORIES = Set.of("APPLICATION_RECEIVED", "APPLICATION_STATUS_UPDATE",
                "INTERVIEW_INVITATION", "ONLINE_ASSESSMENT", "REJECTION", "OFFER",
                "RECRUITER_MESSAGE", "FOLLOW_UP_REQUIRED", "OTHER");
    }
}