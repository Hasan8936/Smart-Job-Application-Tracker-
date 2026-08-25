package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.model.JobPosting;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JobDeduplicatorTest {
    @Test
    void keepsTheFirstPostingForTheSameCanonicalIdentity() {
        JobPosting first = new JobPosting(); first.setDedupeHash("same"); first.setTitle("First");
        JobPosting second = new JobPosting(); second.setDedupeHash("same"); second.setTitle("Second");
        List<JobPosting> result = new JobDeduplicator().deduplicate(List.of(first, second));
        assertEquals(1, result.size());
        assertEquals("First", result.get(0).getTitle());
    }
}