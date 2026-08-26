package com.smartjobtracker.service;

import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class JobActionService {
    private final SavedJobRepository savedJobs; private final JobPostingRepository jobs; private final JobApplicationRepository applications;
    private final JobApplicationService applicationService; private final GeneratedDocumentRepository documents;
    private final CandidateProfileRepository profiles;
    public JobActionService(SavedJobRepository savedJobs, JobPostingRepository jobs, JobApplicationRepository applications,
                            JobApplicationService applicationService, GeneratedDocumentRepository documents, CandidateProfileRepository profiles) {
        this.savedJobs=savedJobs; this.jobs=jobs; this.applications=applications; this.applicationService=applicationService; this.documents=documents; this.profiles=profiles;
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
    @Transactional public GeneratedDocument generate(Long userId, Long jobId, String type) {
        JobPosting job=jobs.findById(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));
        CandidateProfile profile=profiles.findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("Create a verified candidate profile first"));
        String verified = String.join(", ", nonBlank(profile.getSkills(), profile.getProgrammingLanguages(), profile.getFrameworks()));
        String content=switch(type) {
            case "COVER_LETTER" -> "Dear Hiring Team,\n\nI am interested in the " + job.getTitle() + " opportunity at " + job.getCompany() + ". My verified profile includes: " + (verified.isBlank()?"No verified skills listed":verified) + ".\n\nPlease review my application and resume for the complete details.\n\nSincerely,\n[Edit your name]";
            case "COLD_EMAIL" -> "Subject: Interest in " + job.getTitle() + " at " + job.getCompany() + "\n\nHello,\n\nI am reaching out regarding the " + job.getTitle() + " role. My verified skills include " + (verified.isBlank()?"no listed skills":verified) + ". I would welcome the opportunity to discuss the position.\n\nBest,\n[Edit your name]";
            case "INTERVIEW_QUESTIONS" -> "Interview questions for " + job.getTitle() + " at " + job.getCompany() + ":\n\n1. How would you approach the responsibilities described in this role?\n2. Which verified experience from your profile best demonstrates your fit?\n3. What questions do you have about the team and role?";
            case "IMPROVE_RESUME" -> "Resume improvement notes for " + job.getTitle() + ":\n\n- Highlight only these verified skills where supported by your resume: " + (verified.isBlank()?"none listed":verified) + ".\n- Reorder existing, verified experience to address the job description.\n- Do not add skills, projects, achievements, or experience that are not already verified.";
            default -> throw new IllegalArgumentException("Unsupported document type");
        };
        GeneratedDocument document=new GeneratedDocument(); document.setUserId(userId); document.setJobPostingId(jobId); document.setType(type); document.setContent(content); return documents.save(document);
    }
    @Transactional public GeneratedDocument updateDocument(Long userId, Long id, String content) { GeneratedDocument d=documents.findById(id).filter(x -> userId.equals(x.getUserId())).orElseThrow(() -> new IllegalArgumentException("Document not found")); if(content==null||content.isBlank()) throw new IllegalArgumentException("Content is required"); d.setContent(content); d.setUpdatedAt(OffsetDateTime.now()); return documents.save(d); }
    private List<String> nonBlank(String... jsonFields) { return java.util.Arrays.stream(jsonFields).filter(x -> x != null && !x.isBlank() && !"[]".equals(x)).toList(); }
}