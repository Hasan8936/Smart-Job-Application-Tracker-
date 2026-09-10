package com.smartjobtracker.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RuleBasedInterviewPrepProviderTest {

    private final RuleBasedInterviewPrepProvider provider = new RuleBasedInterviewPrepProvider();

    @Test
    void generatesExactlyTheRequestedCount() {
        InterviewPrepProvider.FactProfile facts = new InterviewPrepProvider.FactProfile(
                null, "B.E. Computer Engineering", "Backend Intern at Acme Corp using Java and Spring Boot",
                "Java, Spring Boot, SQL, Docker", "Job Tracker - built with Spring Boot and React");
        List<InterviewPrepProvider.QuestionAnswer> result = provider.generate(
                "We need a backend engineer with Java, Spring Boot, and Docker experience.", facts, 50);
        assertEquals(50, result.size());
        assertTrue(result.stream().allMatch(qa -> qa.question() != null && !qa.question().isBlank()));
        assertTrue(result.stream().allMatch(qa -> qa.suggestedAnswer() != null && !qa.suggestedAnswer().isBlank()));
    }

    @Test
    void groundsTechnicalAnswersInResumeTextWhenTheSkillAppearsThere() {
        InterviewPrepProvider.FactProfile facts = new InterviewPrepProvider.FactProfile(
                null, null, "Backend Intern at Acme Corp using Java and Spring Boot", "Java, Spring Boot", null);
        List<InterviewPrepProvider.QuestionAnswer> result = provider.generate(
                "Looking for a Java and Spring Boot developer.", facts, 20);
        boolean hasGroundedJavaAnswer = result.stream().anyMatch(qa ->
                qa.category() == com.smartjobtracker.model.InterviewQuestionCategory.TECHNICAL
                        && qa.question().toLowerCase().contains("java")
                        && qa.sourceEvidence() != null && qa.sourceEvidence().contains("Acme Corp"));
        assertTrue(hasGroundedJavaAnswer);
    }

    @Test
    void neverFabricatesResumeExperienceForASkillNotOnTheResume() {
        InterviewPrepProvider.FactProfile facts = new InterviewPrepProvider.FactProfile(
                null, null, "Backend Intern at Acme Corp using Java", "Java", null);
        List<InterviewPrepProvider.QuestionAnswer> result = provider.generate(
                "Looking for a Kubernetes and Java developer.", facts, 20);
        // Scope to TECHNICAL: a ROLE_SPECIFIC question is allowed to quote a JD sentence that happens to
        // mention "Kubernetes" -- it doesn't claim the candidate has used it, so it needs no disclaimer.
        result.stream()
                .filter(qa -> qa.category() == com.smartjobtracker.model.InterviewQuestionCategory.TECHNICAL)
                .filter(qa -> qa.question().toLowerCase().contains("kubernetes"))
                .forEach(qa -> assertTrue(qa.suggestedAnswer().contains("isn't explicitly on my resume")));
    }

    @Test
    void coversAllFiveCategoriesWhenEnoughQuestionsAreRequested() {
        InterviewPrepProvider.FactProfile facts = new InterviewPrepProvider.FactProfile(null, null, null, null, null);
        List<InterviewPrepProvider.QuestionAnswer> result = provider.generate("Generic job description.", facts, 50);
        long distinctCategories = result.stream().map(InterviewPrepProvider.QuestionAnswer::category).distinct().count();
        assertEquals(5, distinctCategories);
    }
}
