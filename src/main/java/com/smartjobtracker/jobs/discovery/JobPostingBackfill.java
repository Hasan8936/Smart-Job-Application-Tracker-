package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.repository.JobPostingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Brings already-stored postings in line with the current derived-field rules, so lists reflect them right away
 * (new postings get them during sync): the {@code script_blocked} flag ({@link ScriptFilter}) and
 * {@code country_code} ({@link CountryDetector}). Runs at startup; idempotent; only updates those two columns —
 * rows are never deleted here.
 */
@Component
public class JobPostingBackfill implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(JobPostingBackfill.class);

    private final JobPostingRepository repository;
    private final ScriptFilter filter;

    public JobPostingBackfill(JobPostingRepository repository, ScriptFilter filter) {
        this.repository = repository;
        this.filter = filter;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            refresh();
        } catch (RuntimeException ex) {
            log.warn("Job posting backfill failed; blocked-script and country fields may lag until the next start", ex);
        }
    }

    /** Returns how many postings changed. */
    @Transactional
    public int refresh() {
        List<JobPosting> changed = new ArrayList<>();
        int blocked = 0;
        for (JobPosting job : repository.findAll()) {
            boolean shouldBlock = filter.isBlocked(job.getTitle(), job.getDescription());
            String country = CountryDetector.country(job.getLocation());
            if (shouldBlock) blocked++;
            if (shouldBlock != job.isScriptBlocked() || !Objects.equals(country, job.getCountryCode())) {
                job.setScriptBlocked(shouldBlock);
                job.setCountryCode(country);
                changed.add(job);
            }
        }
        repository.saveAll(changed);
        log.info("Job posting backfill: {} blocked-script posting(s), {} row(s) updated", blocked, changed.size());
        return changed.size();
    }
}
