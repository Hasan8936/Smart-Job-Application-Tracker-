package com.smartjobtracker.service;

import com.smartjobtracker.dto.InterviewPrepDtos;
import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class InterviewPrepService {
    private static final int DEFAULT_COUNT = 50;
    private static final int MAX_COUNT = 80;

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
        try {
            generated = ("gemini".equalsIgnoreCase(aiConfig.getProvider()) ? geminiProvider : ruleBasedProvider).generate(jobDescription, facts, count);
        } catch (RuntimeException ex) {
            generated = ruleBasedProvider.generate(jobDescription, facts, count);
        }

        InterviewPrepSession session = new InterviewPrepSession();
        session.setUserId(userId); session.setResumeId(resume.getId()); session.setApplicationId(applicationId);
        session.setJobDescription(jobDescription); session.setSource(request.source());
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
        return new InterviewPrepProvider.FactProfile(null, education, experience, skills, projects);
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
                qs.stream().map(q -> new InterviewPrepDtos.Question(q.getId(), q.getPosition(), q.getCategory(), q.getQuestion(), q.getSuggestedAnswer(), q.getSourceEvidence())).toList());
    }
}
