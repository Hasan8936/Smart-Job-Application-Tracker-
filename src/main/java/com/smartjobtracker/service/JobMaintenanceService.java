package com.smartjobtracker.service;

import com.smartjobtracker.jobs.discovery.SalaryEstimator;
import com.smartjobtracker.repository.JobPostingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class JobMaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(JobMaintenanceService.class);

    private final JobPostingRepository repository;
    private final SalaryEstimator salaryEstimator;

    public JobMaintenanceService(JobPostingRepository repository, SalaryEstimator salaryEstimator) {
        this.repository = repository;
        this.salaryEstimator = salaryEstimator;
    }

    /** Delete job postings older than 14 days that no user has saved, bookmarked, or applied to. Runs daily at 02:00. */
    @Scheduled(cron = "${app.job-maintenance.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void cleanupStaleJobs() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(14);
        int deleted = repository.deleteStaleJobs(cutoff);
        log.info("Job cleanup: removed {} stale posting(s) older than 14 days", deleted);
    }

    /**
     * Recompute data-backed salary estimates nightly (jobs expire and new reported salaries arrive).
     * Replaces the former Gemini guess: a job only gets an estimate when enough similar postings report one.
     */
    @Scheduled(cron = "${app.job-maintenance.salary-cron:0 30 2 * * *}")
    public void estimateMissingSalaries() {
        int updated = salaryEstimator.refreshEstimates();
        log.info("Salary estimation: updated {} posting(s)", updated);
    }
}
