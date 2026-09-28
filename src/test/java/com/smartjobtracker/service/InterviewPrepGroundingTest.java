package com.smartjobtracker.service;

import com.smartjobtracker.model.InterviewQuestionCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Interview prep answers must be concrete and grounded in the resume, never generic coaching or invented facts. */
class InterviewPrepGroundingTest {

    private static final String RESUME = """
            Alex Doe
            alex.doe@example.test | +1 (555) 123-4567 | linkedin.com/in/alexdoe
            Education
            State University 2020 - 2024
            B.S. Computer Science GPA 3.6
            Experience
            Backend Engineering Intern - Northwind Payments Jun 2023 - Aug 2023
            Tech Stack: Java, Spring Boot, PostgreSQL, Docker, AWS
            - Built a REST API in Spring Boot for merchant refunds, cutting manual refund handling time by 60%.
            - Added PostgreSQL indexes that reduced p95 query latency from 800ms to 120ms.
            - Containerized three services with Docker and deployed them to AWS ECS.
            Projects
            Ride Share Pricing Engine - Node.js, Redis, Kafka
            - Designed a real-time pricing service handling 5,000 simulated concurrent requests; Redis caching
            cut latency 40%.
            Skills
            Java, Spring Boot, Python, SQL, PostgreSQL, Docker, AWS, Redis, Kafka, React, Git
            """;
    private static final String JD = "Backend Engineer, Payments Platform. You will design and build high-throughput Java and Spring Boot "
            + "microservices for payment processing. Own PostgreSQL schema design and query performance. Build event-driven "
            + "pipelines with Kafka. Deploy and operate services on AWS with Docker and Kubernetes.";

    private static InterviewPrepProvider.FactProfile facts() {
        return new InterviewPrepProvider.FactProfile(null, "State University 2020 - 2024\nB.S. Computer Science GPA 3.6",
                "Backend Engineering Intern - Northwind Payments", "Java, Spring Boot, SQL, PostgreSQL, Docker, AWS, Redis, Kafka",
                "Ride Share Pricing Engine", RESUME);
    }

    @Test
    void parsesBulletsWithTheirRoleProjectAndTechStack() {
        List<ResumeBullets.Bullet> bullets = ResumeBullets.parse(RESUME);
        assertEquals(4, bullets.size(), "education/skills lines are not bullets; the wrapped line joins its bullet");
        ResumeBullets.Bullet refund = bullets.get(0);
        assertEquals("Backend Engineering Intern - Northwind Payments", refund.heading());
        assertTrue(refund.tech().contains("Java"));
        assertEquals("As Backend Engineering Intern at Northwind Payments", ResumeBullets.intro(refund));
        assertTrue(ResumeBullets.firstPerson(refund).startsWith("I built a REST API"));
        assertEquals("cutting manual refund handling time by 60%", ResumeBullets.metric(refund));
        ResumeBullets.Bullet pricing = bullets.get(3);
        assertTrue(pricing.text().endsWith("cut latency 40%."), "PDF line-wrap continuation is joined");
        assertEquals("On my Ride Share Pricing Engine project", ResumeBullets.intro(pricing));
    }

    @Test
    void skillMatchingIsWholeWord() {
        assertTrue(ResumeBullets.mentions("Java, Spring Boot", "java"));
        assertFalse(ResumeBullets.mentions("JavaScript and React", "java"));
        assertFalse(ResumeBullets.mentions("PostgreSQL indexes", "sql"));
    }

    @Test
    void offlineAnswersRestateRealResumeBulletsInsteadOfGenericAdvice() {
        List<InterviewPrepProvider.QuestionAnswer> qas = new RuleBasedInterviewPrepProvider().generate(JD, facts(), 15);
        assertEquals(15, qas.size());
        String all = qas.stream().map(InterviewPrepProvider.QuestionAnswer::suggestedAnswer).collect(Collectors.joining("\n"));
        assertTrue(all.contains("cutting manual refund handling time by 60%"));
        assertTrue(all.contains("from 800ms to 120ms"));
        assertFalse(all.contains("Use the STAR method"), "no generic coaching templates");
        assertFalse(all.toLowerCase().contains("map your spring boot experience"), "firstRole bug: a skill is not a role");

        InterviewPrepProvider.QuestionAnswer intro = qas.get(0);
        assertTrue(intro.suggestedAnswer().contains("I have a B.S. Computer Science from State University."), intro.suggestedAnswer());

        InterviewPrepProvider.QuestionAnswer java = qas.stream()
                .filter(q -> q.question().equals("This role requires Java. Walk me through your hands-on experience with it.")).findFirst().orElseThrow();
        assertTrue(java.suggestedAnswer().contains("Northwind Payments"), "Java is in that role's tech stack: " + java.suggestedAnswer());

        assertTrue(qas.stream().noneMatch(q -> q.question().contains("Backend Engineer, Payments Platform.")),
                "a job title is not a responsibility to ask about");
    }

    @Test
    void offlineAnswersNeverInventExperienceForMissingSkills() {
        List<InterviewPrepProvider.QuestionAnswer> qas = new RuleBasedInterviewPrepProvider().generate(JD, facts(), 30);
        qas.stream().filter(q -> q.category() == InterviewQuestionCategory.TECHNICAL && q.question().contains("Kubernetes"))
                .forEach(q -> {
                    assertTrue(q.suggestedAnswer().startsWith("Kubernetes isn't explicitly on my resume"), q.suggestedAnswer());
                    assertEquals("", q.sourceEvidence());
                });
    }

    @Test
    void geminiRequestsAreSplitIntoSmallPerCategoryBatches() {
        List<GeminiInterviewPrepProvider.Batch> batches = GeminiInterviewPrepProvider.plan(50);
        assertEquals(50, batches.stream().mapToInt(GeminiInterviewPrepProvider.Batch::size).sum());
        assertTrue(batches.stream().allMatch(b -> b.size() <= GeminiInterviewPrepProvider.BATCH_SIZE));
        Map<InterviewQuestionCategory, Integer> perCategory = batches.stream().collect(Collectors.groupingBy(
                GeminiInterviewPrepProvider.Batch::category, Collectors.summingInt(GeminiInterviewPrepProvider.Batch::size)));
        assertEquals(5, perCategory.size());
        perCategory.values().forEach(n -> assertEquals(10, n));
        assertEquals(7, GeminiInterviewPrepProvider.plan(7).stream().mapToInt(GeminiInterviewPrepProvider.Batch::size).sum());
    }

    @Test
    void geminiPromptDemandsConcreteFirstPersonAnswersFromTheResume() {
        GeminiInterviewPrepProvider provider = new GeminiInterviewPrepProvider(new com.smartjobtracker.config.AiMatchingConfig(),
                org.springframework.web.client.RestClient.builder(), new com.fasterxml.jackson.databind.ObjectMapper());
        String prompt = provider.prompt(JD, facts(), new GeminiInterviewPrepProvider.Batch(InterviewQuestionCategory.TECHNICAL, 5, 1, 2));
        assertTrue(prompt.contains("EXACTLY 5 TECHNICAL"));
        assertTrue(prompt.contains("NEVER write advice about answering"));
        assertTrue(prompt.contains("Never invent"));
        assertTrue(prompt.contains("cutting manual refund handling time by 60%"), "the resume text itself is sent");
    }

    @Test
    void contactDetailsAreStrippedButDatesAndMetricsKept() {
        String cleaned = InterviewPrepService.withoutContactDetails(RESUME);
        assertFalse(cleaned.contains("alex.doe@example.test"));
        assertFalse(cleaned.contains("555"));
        assertFalse(cleaned.contains("linkedin.com"));
        assertTrue(cleaned.contains("2020 - 2024"));
        assertTrue(cleaned.contains("5,000"));
        assertTrue(cleaned.contains("800ms to 120ms"));
    }

    @Test
    void failedAiBatchesAreFilledOfflineOnlyForTheShortCategories() {
        List<InterviewPrepProvider.QuestionAnswer> offline = new RuleBasedInterviewPrepProvider().generate(JD, facts(), 10);
        List<InterviewPrepProvider.QuestionAnswer> ai = List.of(
                new InterviewPrepProvider.QuestionAnswer(InterviewQuestionCategory.BEHAVIORAL, "AI q1", "AI a1", ""),
                new InterviewPrepProvider.QuestionAnswer(InterviewQuestionCategory.BEHAVIORAL, "AI q2", "AI a2", ""),
                new InterviewPrepProvider.QuestionAnswer(InterviewQuestionCategory.TECHNICAL, "AI q3", "AI a3", ""));
        List<InterviewPrepProvider.QuestionAnswer> merged = InterviewPrepService.topUp(ai, offline, 10);
        assertEquals(10, merged.size());
        assertTrue(merged.containsAll(ai));
        assertEquals(2, merged.stream().filter(q -> q.category() == InterviewQuestionCategory.BEHAVIORAL).count(),
                "behavioral was already full from AI, so no offline behavioral questions are added");
    }
}
