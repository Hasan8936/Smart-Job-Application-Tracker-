package com.smartjobtracker.repository;

import com.smartjobtracker.model.JobPosting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The preferred country is listed first, newest first within each group; the country filter narrows the list. */
@DataJpaTest
@ActiveProfiles("test")
class JobPostingCountryOrderTest {
    @Autowired private JobPostingRepository repository;

    private void save(String id, String country, int daysAgo) {
        JobPosting p = new JobPosting();
        p.setProvider("jobspy"); p.setExternalId(id); p.setDedupeHash("hash-" + id);
        p.setCompany("Acme"); p.setTitle("Engineer " + id); p.setApplyUrl("https://example.test/" + id);
        p.setCountryCode(country);
        p.setPostedAt(OffsetDateTime.now().minusDays(daysAgo));
        repository.saveAndFlush(p);
    }

    private List<String> titles(org.springframework.data.domain.Page<JobPosting> page) {
        return page.getContent().stream().map(JobPosting::getTitle).toList();
    }

    @Test
    void preferredCountryFirstThenNewestAndCountryFilter() {
        save("us-new", "US", 0);
        save("in-old", "IN", 5);
        save("unknown", null, 1);
        save("in-new", "IN", 2);

        var page = repository.search(null, null, null, null, null, null, null, "IN",
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "postedAt")));
        assertEquals(List.of("Engineer in-new", "Engineer in-old", "Engineer us-new", "Engineer unknown"), titles(page));
        assertEquals(4, page.getTotalElements());

        var indiaOnly = repository.search(null, null, null, null, null, null, "IN", "IN",
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "postedAt")));
        assertEquals(List.of("Engineer in-new", "Engineer in-old"), titles(indiaOnly));

        var noPreference = repository.search(null, null, null, null, null, null, null, null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "postedAt")));
        assertEquals("Engineer us-new", titles(noPreference).get(0));

        var fresh = repository.findNewSince(OffsetDateTime.now().minusDays(1), null, null, null, null, null, "IN",
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));
        assertTrue(titles(fresh).subList(0, 2).containsAll(List.of("Engineer in-new", "Engineer in-old")),
                "new-jobs list also puts India first: " + titles(fresh));
    }
}
