package com.smartjobtracker.service;

import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
public class RuleBasedEmailClassifier implements EmailClassifier {
    @Override public Classification classify(EmailInput input) {
        String text = ((input.subject() == null ? "" : input.subject()) + " " + (input.snippet() == null ? "" : input.snippet())).toLowerCase(Locale.ROOT);
        String category = "OTHER";
        if (text.contains("interview")) category = "INTERVIEW_INVITATION";
        else if (text.contains("assessment") || text.contains("coding test")) category = "ONLINE_ASSESSMENT";
        else if (text.contains("offer")) category = "OFFER";
        else if (text.contains("reject") || text.contains("not moving forward")) category = "REJECTION";
        else if (text.contains("application received") || text.contains("thank you for applying")) category = "APPLICATION_RECEIVED";
        else if (text.contains("follow up") || text.contains("follow-up") || text.contains("action required")) category = "FOLLOW_UP_REQUIRED";
        else if (text.contains("recruiter") || text.contains("opportunity")) category = "RECRUITER_MESSAGE";
        else if (text.contains("application") || text.contains("status update")) category = "APPLICATION_STATUS_UPDATE";
        String status = category.equals("INTERVIEW_INVITATION") ? "INTERVIEW" : category.equals("OFFER") ? "OFFER" : category.equals("REJECTION") ? "REJECTED" : null;
        return new Classification(category, null, null, status, null, null, null, text.contains("action required") ? "Review this email" : null, null, 0.65, "rules");
    }
}