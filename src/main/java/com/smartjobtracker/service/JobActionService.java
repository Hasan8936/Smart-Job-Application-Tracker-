package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class JobActionService {
    private static final Logger log = LoggerFactory.getLogger(JobActionService.class);
    private final SavedJobRepository savedJobs; private final JobPostingRepository jobs; private final JobApplicationRepository applications;
    private final JobApplicationService applicationService; private final GeneratedDocumentRepository documents;
    private final CandidateProfileRepository profiles;
    private final ResumeRepository resumes;
    private final GeminiClient gemini;
    private final ObjectMapper mapper;

    public JobActionService(SavedJobRepository savedJobs, JobPostingRepository jobs, JobApplicationRepository applications,
                            JobApplicationService applicationService, GeneratedDocumentRepository documents, CandidateProfileRepository profiles,
                            ResumeRepository resumes, GeminiClient gemini, ObjectMapper mapper) {
        this.savedJobs=savedJobs; this.jobs=jobs; this.applications=applications; this.applicationService=applicationService; this.documents=documents; this.profiles=profiles;
        this.resumes=resumes; this.gemini=gemini; this.mapper=mapper;
    }
    @Transactional public SavedJob setState(Long userId, Long jobId, String state) {
        JobPosting job = jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));
        SavedJob saved=savedJobs.findByUserIdAndJobPostingId(userId, jobId).orElseGet(SavedJob::new);
        saved.setUserId(userId); saved.setJobPostingId(jobId); saved.setState(state); saved.setUpdatedAt(OffsetDateTime.now()); return savedJobs.save(saved);
    }
    @Transactional public SavedJob markApplied(Long userId, Long jobId) {
        JobPosting job=jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));
        JobApplication app=applications.findFirstByUserIdAndCompanyNameIgnoreCaseAndRoleTitleIgnoreCase(userId, job.getCompany(), job.getTitle()).orElse(null);
        if (app == null) { app=new JobApplication(); app.setUserId(userId); app.setCompanyName(job.getCompany()); app.setRoleTitle(job.getTitle()); app.setJobDescription(job.getDescription()); app.setStatus(ApplicationStatus.APPLIED); app.setAppliedDate(LocalDate.now()); app=applicationService.create(app); }
        SavedJob saved=setState(userId, jobId, "APPLIED"); saved.setApplicationId(app.getId()); return savedJobs.save(saved);
    }
    @Transactional(readOnly=true) public List<GeneratedDocument> listDocuments(Long userId, Long jobId) { return documents.findByUserIdAndJobPostingIdOrderByCreatedAtDesc(userId, jobId); }

    @Transactional
    public GeneratedDocument generate(Long userId, Long jobId, String type) {
        JobPosting job=jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));
        String resumeText = latestResume(userId).map(Resume::getExtractedText).filter(text -> text != null && !text.isBlank()).orElse(null);
        if (resumeText == null) throw new IllegalArgumentException("Upload a resume first so this can be grounded in your real background.");
        String jobDescription = job.getDescription() == null || job.getDescription().isBlank()
                ? job.getTitle() + " at " + job.getCompany() : job.getDescription();
        String content;
        try { content = generateWithGemini(type, job, resumeText, jobDescription); }
        catch (RuntimeException ex) { log.warn("Gemini generation failed for type={}, falling back to template", type, ex); content = fallback(type, job, resumeText, ex); }
        GeneratedDocument document=new GeneratedDocument(); document.setUserId(userId); document.setJobPostingId(jobId); document.setType(type); document.setContent(content); return documents.save(document);
    }

    private java.util.Optional<Resume> latestResume(Long userId) {
        return resumes.findByUserId(userId).stream().max(Comparator.comparing(Resume::getUploadedAt));
    }

    private String generateWithGemini(String type, JobPosting job, String resumeText, String jobDescription) {
        String jsonShape = "INTERVIEW_QUESTIONS".equals(type) ? "{\"questions\":[\"string\", ... exactly 10 items]}" : "{\"content\":\"string\"}";
        String task = switch (type) {
            case "COVER_LETTER" -> "Write a concise, specific cover letter (under 300 words) for this job, grounded only in the resume's actual experience -- never invent skills, employers, titles, or achievements not present in the resume. End with \"[Your name]\" as a placeholder signature.";
            case "COLD_EMAIL" -> "Write a short cold outreach email (under 150 words, include a Subject: line) about this role, grounded only in the resume's actual experience -- never invent anything not present in the resume.";
            case "INTERVIEW_QUESTIONS" -> "Generate exactly 10 interview questions this candidate should prepare for, based on the specific overlap and gaps between their resume and this job description. Mix behavioral and technical/role-specific questions relevant to the role.";
            case "IMPROVE_RESUME" -> "Give specific, actionable suggestions (as a bulleted list) for how this candidate should adjust their resume for this specific job -- which existing experience to emphasize, reorder, or rephrase to match the job description. Never suggest adding skills, projects, or experience not already in the resume.";
            default -> throw new IllegalArgumentException("Unsupported document type");
        };
        String system = "You are a career assistant helping a job seeker prepare application materials. " + task
                + " Return JSON only, no markdown fences, matching exactly this shape: " + jsonShape;
        String user = "JOB TITLE: " + job.getTitle() + "\nCOMPANY: " + job.getCompany()
                + "\nJOB DESCRIPTION:\n" + jobDescription + "\n\nRESUME:\n" + resumeText;
        String raw = gemini.complete(system, user, 1500);
        try {
            JsonNode parsed = mapper.readTree(raw);
            if ("INTERVIEW_QUESTIONS".equals(type)) {
                StringBuilder result = new StringBuilder("Interview questions for " + job.getTitle() + " at " + job.getCompany() + ":\n\n");
                int i = 1;
                for (JsonNode question : parsed.path("questions")) result.append(i++).append(". ").append(question.asText()).append("\n");
                return result.toString().stripTrailing();
            }
            String text = parsed.path("content").asText(null);
            if (text == null || text.isBlank()) throw new IllegalStateException("Gemini returned empty content");
            return text;
        } catch (Exception ex) { throw new IllegalStateException("Invalid Gemini output for " + type, ex); }
    }

    /** Used only if Gemini is unavailable/misconfigured -- still grounded in the actual resume text, just without AI-written prose. */
    private String fallback(String type, JobPosting job, String resumeText, RuntimeException cause) {
        String snippet = resumeText.length() > 400 ? resumeText.substring(0, 400) + "..." : resumeText;
        String reason = cause.getMessage() == null ? "unavailable" : cause.getMessage();
        return switch (type) {
            case "COVER_LETTER" -> "Dear Hiring Team,\n\nI am interested in the " + job.getTitle() + " opportunity at " + job.getCompany() + ". Relevant background from my resume:\n\n" + snippet + "\n\nPlease review my attached resume for the complete details.\n\nSincerely,\n[Your name]";
            case "COLD_EMAIL" -> "Subject: Interest in " + job.getTitle() + " at " + job.getCompany() + "\n\nHello,\n\nI am reaching out regarding the " + job.getTitle() + " role. Relevant background from my resume:\n\n" + snippet + "\n\nI would welcome the opportunity to discuss the position.\n\nBest,\n[Your name]";
            case "INTERVIEW_QUESTIONS" -> "Interview questions for " + job.getTitle() + " at " + job.getCompany() + ":\n\n(AI generation failed -- " + reason + " -- here are general prompts to prepare with instead:)\n1. Walk me through your resume and how it led you here.\n2. Which of your past projects is most relevant to this role, and why?\n3. What's a technical challenge from your experience you're proud of solving?\n4. Why this company and this role specifically?\n5. What questions do you have about the team and role?";
            case "IMPROVE_RESUME" -> "Resume improvement notes for " + job.getTitle() + ":\n\n(AI generation failed -- " + reason + ".) Review the job description and reorder your existing, verified experience so the most relevant items appear first. Do not add skills, projects, or experience that aren't already on your resume.";
            default -> throw new IllegalArgumentException("Unsupported document type");
        };
    }

    @Transactional public GeneratedDocument updateDocument(Long userId, Long id, String content) { GeneratedDocument d=documents.findById(id).filter(x -> userId.equals(x.getUserId())).orElseThrow(() -> new IllegalArgumentException("Document not found")); if(content==null||content.isBlank()) throw new IllegalArgumentException("Content is required"); d.setContent(content); d.setUpdatedAt(OffsetDateTime.now()); return documents.save(d); }
}
