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

/**
 * Marks already-stored postings that the current {@link ScriptFilter} rules block, so job lists hide them
 * right away (new postings are filtered during sync). Runs at startup; idempotent; only flips the
 * {@code script_blocked} flag — rows are never deleted here, and changing the configured scripts un-hides
 * postings that no longer match.
 */
@Component
public class ScriptBlockBackfill implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ScriptBlockBackfill.class);

    private final JobPostingRepository repository;
    private final ScriptFilter filter;

    public ScriptBlockBackfill(JobPostingRepository repository, ScriptFilter filter) {
        this.repository = repository;
        this.filter = filter;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            refresh();
        } catch (RuntimeException ex) {
            log.warn("Script-block backfill failed; job lists may still show blocked-script postings until the next start", ex);
        }
    }

    /** Returns how many postings changed state. */
    @Transactional
    public int refresh() {
        List<JobPosting> changed = new ArrayList<>();
        int blocked = 0;
        for (JobPosting job : repository.findAll()) {
            boolean shouldBlock = filter.isBlocked(job.getTitle(), job.getDescription());
            if (shouldBlock) blocked++;
            if (shouldBlock != job.isScriptBlocked()) {
                job.setScriptBlocked(shouldBlock);
                changed.add(job);
            }
        }
        repository.saveAll(changed);
        log.info("Script-block backfill: {} posting(s) blocked, {} changed", blocked, changed.size());
        return changed.size();
    }
}
