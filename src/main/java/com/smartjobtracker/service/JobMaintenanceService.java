package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.repository.JobPostingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class JobMaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(JobMaintenanceService.class);

    private final JobPostingRepository repository;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public JobMaintenanceService(JobPostingRepository repository, GeminiClient geminiClient, ObjectMapper objectMapper) {
        this.repository = repository;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    /** Delete job postings older than 14 days that no user has saved, bookmarked, or applied to. Runs daily at 02:00. */
    @Scheduled(cron = "${app.job-maintenance.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void cleanupStaleJobs() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(14);
        int deleted = repository.deleteStaleJobs(cutoff);
        log.info("Job cleanup: removed {} stale posting(s) older than 14 days", deleted);
    }

    /** Estimate salary via Gemini for recent jobs that lack salary data. Runs daily at 02:30. */
    @Scheduled(cron = "${app.job-maintenance.salary-cron:0 30 2 * * *}")
    @Transactional
    public void estimateMissingSalaries() {
        List<JobPosting> jobs = repository.findRecentWithoutSalary(OffsetDateTime.now().minusDays(14));
        if (jobs.isEmpty()) return;
        log.info("Salary estimation: {} job(s) missing salary data", jobs.size());
        int estimated = 0;
        for (JobPosting job : jobs) {
            try {
                estimateAndSave(job);
                estimated++;
            } catch (Exception ex) {
                log.debug("Salary estimation skipped for job {}: {}", job.getId(), ex.getMessage());
            }
        }
        log.info("Salary estimation: filled in {} of {} job(s)", estimated, jobs.size());
    }

    private void estimateAndSave(JobPosting job) throws Exception {
        String system = """
            You are a salary research assistant. Estimate the salary range for a job posting.
            Reply ONLY with valid JSON: {"salaryMin": <int>, "salaryMax": <int>, "currency": "<ISO code>"}
            Use local market rates (India → INR, US → USD, etc.). Omit any explanation.
            """;
        String descSnippet = job.getDescription() == null ? "" : job.getDescription().substring(0, Math.min(400, job.getDescription().length()));
        String user = "Title: " + job.getTitle() + "\nCompany: " + job.getCompany()
            + "\nLocation: " + (job.getLocation() == null ? "Unknown" : job.getLocation())
            + "\nType: " + (job.getEmploymentType() == null ? "Unknown" : job.getEmploymentType())
            + "\nDescription excerpt: " + descSnippet;

        String raw = geminiClient.complete(system, user, 150);
        JsonNode node = objectMapper.readTree(raw);
        int min = node.path("salaryMin").asInt(0);
        int max = node.path("salaryMax").asInt(0);
        String currency = node.path("currency").asText("INR");
        if (min <= 0 && max <= 0) return;
        job.setSalaryMin(min > 0 ? min : null);
        job.setSalaryMax(max > 0 ? max : null);
        job.setSalaryCurrency(currency);
        job.setSalaryEstimated(true);
        job.setUpdatedAt(OffsetDateTime.now());
        repository.save(job);
    }
}
