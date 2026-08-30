package com.smartjobtracker.repository;

import com.smartjobtracker.model.JobPosting;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class JobPostingRepositoryFindNewSinceTest {

    @Autowired
    private JobPostingRepository repository;

    @Test
    void onlyReturnsPostingsCreatedAfterTheGivenTimestamp() {
        OffsetDateTime since = OffsetDateTime.now().minusHours(1);

        JobPosting oldPosting = posting("greenhouse", "old-1", "Acme");
        oldPosting.setCreatedAt(since.minusDays(1));
        repository.saveAndFlush(oldPosting);

        JobPosting newPosting = posting("greenhouse", "new-1", "Acme");
        newPosting.setCreatedAt(since.plusMinutes(5));
        repository.saveAndFlush(newPosting);

        List<JobPosting> results = repository.findNewSince(since, null, null, null, null, PageRequest.of(0, 20)).getContent();

        assertEquals(1, results.size());
        assertEquals("new-1", results.get(0).getExternalId());
    }

    @Test
    void appliesTheSameFiltersAsTheRegularSearch() {
        OffsetDateTime since = OffsetDateTime.now().minusHours(1);

        JobPosting matching = posting("greenhouse", "match-1", "Acme");
        matching.setTitle("Backend Engineer");
        matching.setCreatedAt(since.plusMinutes(1));
        repository.saveAndFlush(matching);

        JobPosting nonMatching = posting("greenhouse", "no-match-1", "Acme");
        nonMatching.setTitle("Sales Representative");
        nonMatching.setCreatedAt(since.plusMinutes(1));
        repository.saveAndFlush(nonMatching);

        List<JobPosting> results = repository.findNewSince(since, "backend", null, null, null, PageRequest.of(0, 20)).getContent();

        assertEquals(1, results.size());
        assertTrue(results.get(0).getTitle().toLowerCase().contains("backend"));
    }

    @Test
    void returnsNothingWhenNoPostingsAreNewerThanSince() {
        OffsetDateTime since = OffsetDateTime.now();

        JobPosting oldPosting = posting("lever", "old-2", "Acme");
        oldPosting.setCreatedAt(since.minusDays(2));
        repository.saveAndFlush(oldPosting);

        List<JobPosting> results = repository.findNewSince(since, null, null, null, null, PageRequest.of(0, 20)).getContent();

        assertTrue(results.isEmpty());
    }

    private JobPosting posting(String provider, String externalId, String company) {
        JobPosting posting = new JobPosting();
        posting.setProvider(provider);
        posting.setExternalId(externalId);
        posting.setDedupeHash(provider + ":" + externalId);
        posting.setCompany(company);
        posting.setTitle("Role");
        posting.setApplyUrl("https://example.com/apply/" + externalId);
        return posting;
    }
}
