package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.jobs.provider.JobProvider;
import com.smartjobtracker.jobs.provider.JobProvider.ProviderJob;
import com.smartjobtracker.model.JobPosting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobNormalizerTest {
    private final JobNormalizer normalizer = new JobNormalizer();

    @Test
    void stripsMarkupAndLeavesUnknownFieldsUnavailable() {
        JobProvider.JobPostingCandidate candidate = JobProvider.JobPostingCandidate.from(new ProviderJob("1", " Acme ", "Engineer", "Remote",
                null, null, "https://example.test/job", "not-a-date", "<p>Build <b>things</b></p>", null, null, null, null, "{}"), "official");
        JobPosting job = normalizer.normalize(candidate);
        assertEquals("Acme", job.getCompany());
        assertEquals("Build things", job.getDescription());
        assertNull(job.getPostedAt());
        assertNull(job.getSalaryMin());
        assertNotNull(job.getDedupeHash());
    }
}