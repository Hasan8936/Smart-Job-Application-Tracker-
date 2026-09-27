package com.smartjobtracker.repository;

import com.smartjobtracker.model.JobPosting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Blocked-script postings stay stored but never appear in /api/jobs or /api/jobs/new results. */
@DataJpaTest
@ActiveProfiles("test")
class JobPostingScriptBlockedQueryTest {
    @Autowired
    private JobPostingRepository repository;

    private JobPosting save(String id, String title, boolean blocked) {
        JobPosting p = new JobPosting();
        p.setProvider("jobspy"); p.setExternalId(id); p.setDedupeHash("hash-" + id);
        p.setCompany("Acme"); p.setTitle(title); p.setApplyUrl("https://example.test/" + id);
        p.setScriptBlocked(blocked);
        return repository.saveAndFlush(p);
    }

    @Test
    void listQueriesExcludeBlockedPostingsButTheRowIsKept() {
        JobPosting visible = save("1", "Backend Engineer", false);
        JobPosting hidden = save("2", "سرآشپز حرفه‌ای", true);

        var search = repository.search(null, null, null, null, null, null, PageRequest.of(0, 20));
        assertEquals(1, search.getTotalElements());
        assertEquals(visible.getId(), search.getContent().get(0).getId());

        var fresh = repository.findNewSince(OffsetDateTime.now().minusDays(1), null, null, null, null, PageRequest.of(0, 20));
        assertEquals(1, fresh.getTotalElements());

        assertTrue(repository.findById(hidden.getId()).isPresent(), "saved/applied references still resolve");
    }
}
