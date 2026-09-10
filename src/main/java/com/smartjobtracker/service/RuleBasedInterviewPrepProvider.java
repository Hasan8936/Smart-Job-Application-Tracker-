package com.smartjobtracker.service;

import com.smartjobtracker.model.InterviewQuestionCategory;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Deterministic, offline interview-question generator (no network call). Used whenever the AI
 * provider is not configured or fails, so the feature always returns something rather than an
 * error. Behavioral / situational / motivation questions come from a fixed, well-known bank;
 * technical and role-specific questions are grounded in whichever skill terms actually appear in
 * both the job description and the resume's extracted facts, so answers can point back to real
 * resume text instead of invented specifics. Every pool is cycled with a circular index so
 * topping up to the requested count advances through the pool rather than repeating item 0.
 */
@Component("ruleBasedInterviewPrepProvider")
public class RuleBasedInterviewPrepProvider implements InterviewPrepProvider {

    /** A moderate, generic tech/skill vocabulary used only to find JD/resume overlap terms -- not exhaustive. */
    private static final List<String> SKILL_VOCABULARY = List.of(
            "java", "python", "javascript", "typescript", "c++", "c#", "sql", "nosql", "react", "angular", "vue",
            "node", "spring", "spring boot", "django", "flask", "docker", "kubernetes", "aws", "azure", "gcp",
            "rest api", "graphql", "microservices", "ci/cd", "git", "linux", "machine learning", "deep learning",
            "data analysis", "pandas", "numpy", "tensorflow", "pytorch", "excel", "power bi", "tableau", "agile",
            "scrum", "testing", "unit testing", "automation", "html", "css", "postgresql", "mysql", "mongodb",
            "redis", "kafka", "communication", "leadership", "project management", "quality assurance", "qa",
            "manual testing", "selenium", "api testing", "postman", "jira", "matlab", "embedded systems",
            "control systems", "instrumentation", "plc", "scada", "signal processing");

    private static final List<String> BEHAVIORAL_TEMPLATES = List.of(
            "Tell me about yourself and what led you to this role.",
            "Describe a time you had to meet a tight deadline. What did you do?",
            "Tell me about a mistake you made at work or in a project, and how you handled it.",
            "Describe a situation where you disagreed with a teammate or supervisor. How did you resolve it?",
            "Tell me about a time you had to learn something new quickly to complete a task.",
            "Give an example of when you took initiative without being asked.",
            "Describe a project you're most proud of and why.",
            "Tell me about a time you received critical feedback. How did you respond?",
            "Describe a time you had to work with a difficult team member.",
            "Tell me about a time you had to juggle multiple priorities at once.");

    private static final List<String> SITUATIONAL_TEMPLATES = List.of(
            "If you were assigned a task with unclear requirements, how would you approach it?",
            "How would you handle discovering a serious issue right before a deadline?",
            "If two stakeholders gave you conflicting priorities, how would you decide what to work on first?",
            "How would you approach a project in a technology you haven't used before?",
            "If you noticed a process that was inefficient, how would you go about improving it?",
            "How would you handle a situation where you didn't understand feedback you were given?",
            "What would you do if you realized, midway through a task, that your approach wasn't working?");

    private static final List<String> COMPANY_MOTIVATION_TEMPLATES = List.of(
            "Why are you interested in this role?",
            "What do you know about this company or team, and why does it appeal to you?",
            "Where do you see yourself professionally in the next few years?",
            "What are you looking for in your next opportunity?",
            "Why should we hire you over other candidates?",
            "What part of this job description excites you the most?",
            "What questions do you have for us about the role or team?");

    @Override
    public List<QuestionAnswer> generate(String jobDescription, FactProfile facts, int count) {
        List<String> skills = overlapSkills(jobDescription, facts);
        List<String> jdSentences = jdResponsibilitySentences(jobDescription);
        int perCategory = Math.max(1, (count + 4) / 5);

        List<QuestionAnswer> out = new ArrayList<>();
        appendCycled(out, InterviewQuestionCategory.BEHAVIORAL, perCategory,
                i -> BEHAVIORAL_TEMPLATES.get(i % BEHAVIORAL_TEMPLATES.size()), (q, i) -> genericAnswer(facts), (q, i) -> "");
        appendCycled(out, InterviewQuestionCategory.TECHNICAL, perCategory,
                i -> technicalQuestion(skills, facts, i), (q, i) -> technicalAnswer(skills, facts, i), (q, i) -> technicalEvidence(skills, facts, i));
        appendCycled(out, InterviewQuestionCategory.ROLE_SPECIFIC, perCategory,
                i -> roleQuestion(jdSentences, i), (q, i) -> roleAnswer(jdSentences, facts, i), (q, i) -> trimmed(facts.experience(), 200));
        appendCycled(out, InterviewQuestionCategory.SITUATIONAL, perCategory,
                i -> SITUATIONAL_TEMPLATES.get(i % SITUATIONAL_TEMPLATES.size()), (q, i) -> genericAnswer(facts), (q, i) -> "");
        int remaining = Math.max(perCategory, count - out.size());
        appendCycled(out, InterviewQuestionCategory.COMPANY_AND_MOTIVATION, remaining,
                i -> COMPANY_MOTIVATION_TEMPLATES.get(i % COMPANY_MOTIVATION_TEMPLATES.size()), (q, i) -> genericAnswer(facts), (q, i) -> "");

        if (out.size() > count) return new ArrayList<>(out.subList(0, count));
        int i = perCategory;
        while (out.size() < count) {
            out.add(new QuestionAnswer(InterviewQuestionCategory.TECHNICAL, technicalQuestion(skills, facts, i), technicalAnswer(skills, facts, i), technicalEvidence(skills, facts, i)));
            i++;
        }
        return out;
    }

    private interface IndexedText { String apply(int index); }
    private interface IndexedAnswer { String apply(String question, int index); }

    private void appendCycled(List<QuestionAnswer> out, InterviewQuestionCategory category, int howMany,
                               IndexedText question, IndexedAnswer answer, IndexedAnswer evidence) {
        for (int i = 0; i < howMany; i++) {
            String q = question.apply(i);
            out.add(new QuestionAnswer(category, q, answer.apply(q, i), evidence.apply(q, i)));
        }
    }

    private String technicalQuestion(List<String> skills, FactProfile facts, int index) {
        if (skills.isEmpty()) return index == 0
                ? "Walk me through the technical skills on your resume that are most relevant to this role."
                : "What's an area of your technical skill set you'd most like to grow, and why?";
        String skill = skills.get(index % skills.size());
        return "This role involves " + skill + " -- can you walk me through your experience with it?";
    }

    private String technicalAnswer(List<String> skills, FactProfile facts, int index) {
        if (skills.isEmpty()) return "I'd walk through: " + trimmed(facts.skills(), 300) + ", and connect each to the responsibilities in the job description.";
        String skill = skills.get(index % skills.size());
        boolean inResume = containsIgnoreCase(facts.experience(), skill) || containsIgnoreCase(facts.projects(), skill) || containsIgnoreCase(facts.skills(), skill);
        if (inResume) return "I've used " + skill + " in this context: " + evidenceFor(facts, skill) + ". I'd explain the specific problem it solved and what I'd bring to how this team uses it.";
        return skill + " isn't explicitly on my resume, so I'd be honest about that, describe closely related tools I have used, and outline how quickly I typically pick up a new stack.";
    }

    private String technicalEvidence(List<String> skills, FactProfile facts, int index) {
        if (skills.isEmpty()) return trimmed(facts.skills(), 300);
        String skill = skills.get(index % skills.size());
        boolean inResume = containsIgnoreCase(facts.experience(), skill) || containsIgnoreCase(facts.projects(), skill) || containsIgnoreCase(facts.skills(), skill);
        return inResume ? evidenceFor(facts, skill) : "";
    }

    private String roleQuestion(List<String> jdSentences, int index) {
        if (jdSentences.isEmpty()) return "Which of your past experiences best prepares you for the day-to-day of this role?";
        return "The job description says: \"" + jdSentences.get(index % jdSentences.size()) + "\" -- how does your background prepare you for that?";
    }

    private String roleAnswer(List<String> jdSentences, FactProfile facts, int index) {
        return "Drawing on " + trimmed(facts.experience(), 200) + (blank(facts.projects()) ? "" : " and " + trimmed(facts.projects(), 200)) + ", I'd connect that directly to this responsibility.";
    }

    private String genericAnswer(FactProfile facts) {
        String anchor = !blank(facts.experience()) ? trimmed(facts.experience(), 250)
                : !blank(facts.projects()) ? trimmed(facts.projects(), 250) : trimmed(facts.education(), 250);
        return "I'd structure this using a specific example from " + (blank(anchor) ? "my background" : anchor) + ", following a situation-action-result shape.";
    }

    private String evidenceFor(FactProfile facts, String skill) {
        if (containsIgnoreCase(facts.experience(), skill)) return trimmed(facts.experience(), 300);
        if (containsIgnoreCase(facts.projects(), skill)) return trimmed(facts.projects(), 300);
        return trimmed(facts.skills(), 300);
    }

    private List<String> overlapSkills(String jobDescription, FactProfile facts) {
        String jd = jobDescription == null ? "" : jobDescription.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> found = new LinkedHashSet<>();
        for (String skill : SKILL_VOCABULARY) if (jd.contains(skill)) found.add(skill);
        List<String> grounded = new ArrayList<>();
        List<String> ungrounded = new ArrayList<>();
        for (String skill : found) {
            boolean inResume = containsIgnoreCase(facts.skills(), skill) || containsIgnoreCase(facts.experience(), skill) || containsIgnoreCase(facts.projects(), skill);
            (inResume ? grounded : ungrounded).add(skill);
        }
        grounded.addAll(ungrounded);
        return grounded;
    }

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?;])\\s+|\\n+");

    private List<String> jdResponsibilitySentences(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) return List.of();
        List<String> sentences = new ArrayList<>();
        for (String raw : SENTENCE_SPLIT.split(jobDescription)) {
            String s = raw.replaceAll("^[\\s\\-•*]+", "").trim();
            if (s.length() < 25 || s.length() > 220) continue;
            sentences.add(s);
            if (sentences.size() >= 15) break;
        }
        return sentences;
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && needle != null && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private String trimmed(String value, int max) {
        if (value == null || value.isBlank()) return "";
        String v = value.replaceAll("\\s+", " ").trim();
        return v.length() > max ? v.substring(0, max) + "..." : v;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
