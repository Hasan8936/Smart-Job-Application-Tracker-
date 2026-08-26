package com.smartjobtracker.service;

import com.smartjobtracker.model.ApplicationFieldType;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class RuleBasedApplicationPreparationProvider implements ApplicationPreparationProvider {
    @Override public List<Proposal> suggest(String jobDescription, FactProfile facts, List<FieldRequest> fields) {
        List<Proposal> result = new ArrayList<>();
        for (FieldRequest field : fields) {
            String value = value(facts, field.fieldType());
            if (value == null || value.isBlank()) continue;
            result.add(new Proposal(field.externalField(), field.fieldType(), value, value, "Copied from the user's verified application profile."));
        }
        return result;
    }
    private String value(FactProfile f, ApplicationFieldType type) { return switch(type) {
        case NAME -> f.name(); case EMAIL -> f.email(); case PHONE -> f.phone(); case EDUCATION -> f.education(); case EXPERIENCE -> f.experience();
        case SKILLS -> f.skills(); case GITHUB -> f.github(); case LINKEDIN -> f.linkedin(); case PORTFOLIO -> f.portfolio(); case RESUME -> f.resumeFileName();
    }; }
}