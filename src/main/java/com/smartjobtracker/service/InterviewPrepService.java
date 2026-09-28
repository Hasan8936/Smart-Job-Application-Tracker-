package com.smartjobtracker.service;

import com.smartjobtracker.dto.InterviewPrepDtos;
import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class InterviewPrepService {
    private static final Logger log = LoggerFactory.getLogger(InterviewPrepService.class);
    private static final int DEFAULT_COUNT = 50;
    private static final int MAX_COUNT = 80;
    /** Resume text passed to generators; contact details are stripped first (the model never needs them). */
    private static final int RESUME_TEXT_CHARS = 12_000;
    static final String GENERATOR_AI = "AI", GENERATOR_OFFLINE = "OFFLINE", GENERATOR_MIXED = "MIXED";

    private final InterviewPrepSessionRepository sessions;
    private final InterviewPrepQuestionRepository questions;
    private final ResumeRepository resumes;
    private final JobApplicationRepository applications;
    private final ResumeProfileExtractor extractor;
    private final JobDescriptionFetcher fetcher;
    private final InterviewPrepProvider ruleBasedProvider;
    private final InterviewPrepProvider geminiProvider;
    private final com.smartjobtracker.config.AiMatchingConfig aiConfig;

    public InterviewPrepService(InterviewPrepSessionRepository sessions, InterviewPrepQuestionRepository questions,
            ResumeRepository resumes, JobApplicationRepository applications, ResumeProfileExtractor extractor,
            JobDescriptionFetcher fetcher, @Qualifier("ruleBasedInterviewPrepProvider") InterviewPrepProvider ruleBasedProvider,
            @Qualifier("geminiInterviewPrepProvider") InterviewPrepProvider geminiProvider,
            com.smartjobtracker.config.AiMatchingConfig aiConfig) {
        this.sessions = sessions; this.questions = questions; this.resumes = resumes; this.applications = applications;
        this.extractor = extractor; this.fetcher = fetcher; this.ruleBasedProvider = ruleBasedProvider;
        this.geminiProvider = geminiProvider; this.aiConfig = aiConfig;
    }

    @Transactional
    public InterviewPrepDtos.Session generate(Long userId, InterviewPrepDtos.GenerateRequest request) {
        Resume resume = resumes.findById(request.resumeId()).filter(r -> Objects.equals(r.getUserId(), userId))
                .orElseThrow(() -> new IllegalArgumentException("Resume not found"));
        Long applicationId = null;
        String jobDescription;
        switch (request.source()) {
            case TEXT -> {
                if (blank(request.jobDescriptionText())) throw new IllegalArgumentException("Paste a job description first");
                jobDescription = request.jobDescriptionText();
            }
            case URL -> {
                if (blank(request.jobDescriptionUrl())) throw new IllegalArgumentException("Enter a job posting URL first");
                jobDescription = fetcher.fetch(request.jobDescriptionUrl());
            }
            case SAVED_APPLICATION -> {
                if (request.applicationId() == null) throw new IllegalArgumentException("Select a saved application first");
                JobApplication application = applications.findByIdAndUserId(request.applicationId(), userId)
                        .orElseThrow(() -> new IllegalArgumentException("Saved application not found"));
                if (blank(application.getJobDescription())) throw new IllegalArgumentException("That saved application has no job description to prepare from");
                jobDescription = application.getJobDescription();
                applicationId = application.getId();
            }
            default -> throw new IllegalArgumentException("Unknown job description source");
        }

        int count = request.questionCount() == null ? DEFAULT_COUNT : Math.max(5, Math.min(MAX_COUNT, request.questionCount()));
        InterviewPrepProvider.FactProfile facts = facts(resume);

        List<InterviewPrepProvider.QuestionAnswer> generated;
        String generator;
        boolean hasGeminiKey = aiConfig.getApiKey() != null && !aiConfig.getApiKey().isBlank();
        if (!hasGeminiKey) {
            generated = ruleBasedProvider.generate(jobDescription, facts, count);
            generator = GENERATOR_OFFLINE;
        } else {
            try {
                generated = geminiProvider.generate(jobDescription, facts, count);
                generator = GENERATOR_AI;
            } catch (RuntimeException ex) {
                log.warn("Gemini interview prep failed for userId={} ({}); using offline drafts", userId, ex.getMessage());
                generated = List.of();
                generator = GENERATOR_OFFLINE;
            }
            if (generated.size() < count) {
                // Fill only the categories the AI batches left short, so one failed batch doesn't discard the rest.
                generated = topUp(generated, ruleBasedProvider.generate(jobDescription, facts, count), count);
                if (GENERATOR_AI.equals(generator)) generator = GENERATOR_MIXED;
            }
        }
        log.info("Interview prep for userId={}: {} questions, generator={}", userId, generated.size(), generator);

        InterviewPrepSession session = new InterviewPrepSession();
        session.setUserId(userId); session.setResumeId(resume.getId()); session.setApplicationId(applicationId);
        session.setJobDescription(jobDescription); session.setSource(request.source());
        session.setGenerator(generator);
        session = sessions.save(session);

        List<InterviewPrepQuestion> stored = new ArrayList<>();
        int position = 0;
        for (InterviewPrepProvider.QuestionAnswer qa : generated) {
            if (blank(qa.question()) || blank(qa.suggestedAnswer())) continue;
            InterviewPrepQuestion question = new InterviewPrepQuestion();
            question.setSessionId(session.getId()); question.setPosition(position++);
            question.setCategory(qa.category() == null ? InterviewQuestionCategory.ROLE_SPECIFIC : qa.category());
            question.setQuestion(qa.question()); question.setSuggestedAnswer(qa.suggestedAnswer());
            question.setSourceEvidence(qa.sourceEvidence());
            stored.add(questions.save(question));
        }
        return toSession(session, stored);
    }

    @Transactional(readOnly = true)
    public InterviewPrepDtos.Session getSession(Long userId, Long id) {
        InterviewPrepSession session = sessions.findByIdAndUserId(id, userId).orElseThrow(() -> new IllegalArgumentException("Interview prep session not found"));
        return toSession(session, questions.findBySessionIdOrderByPositionAsc(session.getId()));
    }

    @Transactional(readOnly = true)
    public List<InterviewPrepDtos.SessionSummary> listSessions(Long userId) {
        return sessions.findByUserIdOrderByCreatedAtDesc(userId).stream().map(session -> new InterviewPrepDtos.SessionSummary(
                session.getId(), preview(session.getJobDescription()), session.getSource(),
                questions.findBySessionIdOrderByPositionAsc(session.getId()).size(), session.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public String exportMarkdown(Long userId, Long id) {
        InterviewPrepDtos.Session session = getSession(userId, id);
        StringBuilder sb = new StringBuilder("# Interview preparation\n\n");
        InterviewQuestionCategory current = null;
        for (InterviewPrepDtos.Question q : session.questions()) {
            if (q.category() != current) { sb.append("\n## ").append(label(q.category())).append("\n\n"); current = q.category(); }
            sb.append(q.position() + 1).append(". **Q: ").append(q.question()).append("**\n\n");
            sb.append("   A: ").append(q.suggestedAnswer()).append("\n\n");
            if (!blank(q.sourceEvidence())) sb.append("   _Resume evidence: ").append(q.sourceEvidence()).append("_\n\n");
        }
        return sb.toString();
    }

    private String label(InterviewQuestionCategory category) {
        return switch (category) {
            case BEHAVIORAL -> "Behavioral";
            case TECHNICAL -> "Technical";
            case ROLE_SPECIFIC -> "Role-specific";
            case SITUATIONAL -> "Situational";
            case COMPANY_AND_MOTIVATION -> "Company & motivation";
        };
    }

    private InterviewPrepProvider.FactProfile facts(Resume resume) {
        String text = resume.getExtractedText() == null ? "" : resume.getExtractedText();
        ResumeProfileExtractor.ExtractedProfile extracted = extractor.extract(text);
        String skills = String.join(", ", distinct(extracted.getSkills(), extracted.getProgrammingLanguages(), extracted.getFrameworks()));
        String education = String.join("\n", extracted.getEducation());
        String experience = String.join("\n", extracted.getExperience());
        String projects = String.join("\n", extracted.getProjects());
        String resumeText = withoutContactDetails(text);
        if (resumeText.length() > RESUME_TEXT_CHARS) resumeText = resumeText.substring(0, RESUME_TEXT_CHARS);
        return new InterviewPrepProvider.FactProfile(null, education, experience, skills, projects, resumeText);
    }

    /** Emails, URLs and phone numbers carry no interview content and shouldn't be sent to an AI provider. */
    static String withoutContactDetails(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+", " ")
                .replaceAll("(?i)\\b(https?://|www\\.)\\S+", " ")
                .replaceAll("(?i)\\b(linkedin|github)\\.com/\\S*", " ")
                // Phone numbers: 10+ digits. A plain digit-run pattern would also eat "2020 - 2024" date ranges.
                .replaceAll("\\+?\\(?\\d{2,4}\\)?[ -]?\\d{3,5}[ -]?\\d{3,5}(?![\\d%])", " ")
                .replaceAll("[ \\t]{2,}", " ");
    }

    /** AI results first; then offline questions for whichever categories are still short of the offline split. */
    static List<InterviewPrepProvider.QuestionAnswer> topUp(List<InterviewPrepProvider.QuestionAnswer> ai,
                                                           List<InterviewPrepProvider.QuestionAnswer> offline, int count) {
        List<InterviewPrepProvider.QuestionAnswer> out = new ArrayList<>(ai);
        java.util.Map<InterviewQuestionCategory, Long> target = new java.util.EnumMap<>(InterviewQuestionCategory.class);
        java.util.Map<InterviewQuestionCategory, Long> have = new java.util.EnumMap<>(InterviewQuestionCategory.class);
        for (InterviewPrepProvider.QuestionAnswer qa : offline) target.merge(qa.category(), 1L, Long::sum);
        for (InterviewPrepProvider.QuestionAnswer qa : ai) have.merge(qa.category(), 1L, Long::sum);
        for (InterviewPrepProvider.QuestionAnswer qa : offline) {
            if (out.size() >= count) break;
            if (have.getOrDefault(qa.category(), 0L) < target.getOrDefault(qa.category(), 0L)) {
                out.add(qa);
                have.merge(qa.category(), 1L, Long::sum);
            }
        }
        for (InterviewPrepProvider.QuestionAnswer qa : offline) {   // still short (AI over-filled one category): take any
            if (out.size() >= count) break;
            if (!out.contains(qa)) out.add(qa);
        }
        out.sort(java.util.Comparator.comparingInt(qa -> qa.category().ordinal()));
        return out;
    }

    @SafeVarargs
    private List<String> distinct(List<String>... lists) {
        java.util.LinkedHashSet<String> combined = new java.util.LinkedHashSet<>();
        for (List<String> list : lists) combined.addAll(list);
        return new ArrayList<>(combined);
    }

    private String preview(String jobDescription) {
        if (blank(jobDescription)) return "";
        String v = jobDescription.replaceAll("\\s+", " ").trim();
        return v.length() > 140 ? v.substring(0, 140) + "..." : v;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private InterviewPrepDtos.Session toSession(InterviewPrepSession session, List<InterviewPrepQuestion> qs) {
        return new InterviewPrepDtos.Session(session.getId(), session.getJobDescription(), session.getSource(),
                session.getResumeId(), session.getApplicationId(), session.getCreatedAt(),
                qs.stream().map(q -> new InterviewPrepDtos.Question(q.getId(), q.getPosition(), q.getCategory(), q.getQuestion(), q.getSuggestedAnswer(), q.getSourceEvidence())).toList(),
                session.getGenerator());
    }
}
