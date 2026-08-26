package com.smartjobtracker.service;

import com.smartjobtracker.model.ApplicationFieldType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationPreparationProviderTest {
    @Test
    void createsAnswersOnlyForFactsThatExist() {
        ApplicationPreparationProvider.FactProfile facts = new ApplicationPreparationProvider.FactProfile(
                "Jane Doe", "jane@example.com", null, "BSc Computer Science", null,
                "Java, Git", null, "https://linkedin.com/in/jane", null, "resume.pdf");
        List<ApplicationPreparationProvider.Proposal> result = new RuleBasedApplicationPreparationProvider().suggest(
                "Need Java and Python", facts, List.of(
                        new ApplicationPreparationProvider.FieldRequest("full_name", ApplicationFieldType.NAME),
                        new ApplicationPreparationProvider.FieldRequest("phone", ApplicationFieldType.PHONE),
                        new ApplicationPreparationProvider.FieldRequest("skills", ApplicationFieldType.SKILLS)));

        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(item -> item.fieldType() == ApplicationFieldType.PHONE));
        assertTrue(result.stream().allMatch(item -> item.value().equals(item.evidence())));
    }

    @Test
    void neverUsesJobDescriptionAsAnAnswerSource() {
        ApplicationPreparationProvider.FactProfile facts = new ApplicationPreparationProvider.FactProfile(
                "Jane Doe", "jane@example.com", null, null, null, null, null, null, null, "resume.pdf");
        List<ApplicationPreparationProvider.Proposal> result = new RuleBasedApplicationPreparationProvider().suggest(
                "Python, Kubernetes, 10 years experience", facts,
                List.of(new ApplicationPreparationProvider.FieldRequest("skills", ApplicationFieldType.SKILLS)));
        assertTrue(result.isEmpty());
    }
}