package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.repository.JobPostingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class SalaryEstimatorTest {

    private static JobPosting posting(String title, String location, Integer min, Integer max, String currency, String source) {
        JobPosting p = new JobPosting();
        p.setTitle(title); p.setLocation(location);
        p.setSalaryMin(min); p.setSalaryMax(max); p.setSalaryCurrency(currency);
        p.setSalarySource(source); p.setSalaryPeriod(source == null ? null : "YEAR");
        return p;
    }

    private static List<JobPosting> peers(int count, String title, String location, String currency) {
        List<JobPosting> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(posting(title, location, 100_000 + i * 10_000, 150_000 + i * 10_000, currency, "PROVIDER"));
        return out;
    }

    private final SalaryEstimator estimator = new SalaryEstimator(mock(JobPostingRepository.class));

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "Senior Software Engineer, Backend | backend          | senior",
            "Software Engineer II              | software         | mid",
            "Staff Machine Learning Engineer   | machine-learning | staff",
            "Account Executive, AI Sales       | sales            | mid",
            "Recruiter, Sales & G&A            | recruiting       | mid",
            "Data Scientist - New Grad         | data-science     | junior",
            "Software Engineering Intern       | software         | intern",
            "Senior Product Manager            | product-manager  | senior",
            "Head of People Operations         | null             | staff",
    })
    void classifiesTitles(String title, String family, String seniority) {
        assertEquals(family, SalaryEstimator.roleFamily(title));
        assertEquals(seniority, SalaryEstimator.seniority(title));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "San Francisco, CA       | US",
            "Remote - USA            | US",
            "Chicago, IL             | US",
            "Washington, D.C.        | US",
            "Bengaluru, Karnataka    | IN",
            "London                  | GB",
            "Toronto, ON             | CA",
            "Berlin, Germany         | DE",
            "Remote                  | null",
            "N/A                     | null",
    })
    void detectsCountry(String location, String country) {
        assertEquals(country, SalaryEstimator.country(location));
    }

    @Test
    void estimatesFromTheMedianOfAtLeastFivePeers() {
        var pool = estimator.pool(peers(5, "Backend Engineer", "Seattle, WA", "USD"));
        var e = estimator.estimate(posting("Backend Developer", "Austin, TX", null, null, null, null), pool).orElseThrow();
        assertEquals(120_000, e.min());
        assertEquals(170_000, e.max());
        assertEquals("USD", e.currency());
        assertEquals(5, e.sampleSize());
    }

    @Test
    void noEstimateWithTooFewPeersOrUnknownRoleOrCountry() {
        var pool = estimator.pool(peers(4, "Backend Engineer", "Seattle, WA", "USD"));
        assertTrue(estimator.estimate(posting("Backend Engineer", "Austin, TX", null, null, null, null), pool).isEmpty());

        var big = estimator.pool(peers(8, "Backend Engineer", "Seattle, WA", "USD"));
        assertTrue(estimator.estimate(posting("Backend Engineer", "Remote", null, null, null, null), big).isEmpty(), "country unknown");
        assertTrue(estimator.estimate(posting("Barista", "Seattle, WA", null, null, null, null), big).isEmpty(), "role unknown");
        assertTrue(estimator.estimate(posting("Senior Backend Engineer", "Seattle, WA", null, null, null, null), big).isEmpty(), "different seniority");
        assertTrue(estimator.estimate(posting("Backend Engineer", "Pune, India", null, null, null, null), big).isEmpty(), "different country");
    }

    @Test
    void neverMixesCurrencies() {
        List<JobPosting> mixed = new ArrayList<>(peers(3, "Backend Engineer", "Toronto, ON", "CAD"));
        mixed.addAll(peers(3, "Backend Engineer", "Toronto, ON", "USD"));
        assertTrue(estimator.estimate(posting("Backend Engineer", "Toronto, ON", null, null, null, null), estimator.pool(mixed)).isEmpty());
    }

    @Test
    void applyNeverOverwritesReportedSalaries() {
        var estimate = new SalaryEstimator.Estimate(1, 2, "USD", 9);
        JobPosting provider = posting("Backend Engineer", "Seattle, WA", 150_000, 180_000, "USD", "PROVIDER");
        assertFalse(estimator.apply(provider, estimate));
        assertEquals(150_000, provider.getSalaryMin());

        // Pre-V25 row with a real source salary (no salary_source recorded yet, not flagged estimated).
        JobPosting legacy = posting("Backend Engineer", "Seattle, WA", 150_000, 180_000, "USD", null);
        assertFalse(estimator.apply(legacy, null));
        assertEquals(150_000, legacy.getSalaryMin());
    }

    @Test
    void applySetsEstimateAndClearsItWhenEvidenceDisappears() {
        JobPosting job = posting("Backend Engineer", "Seattle, WA", null, null, null, null);
        assertTrue(estimator.apply(job, new SalaryEstimator.Estimate(120_000, 170_000, "USD", 6)));
        assertEquals("ESTIMATE", job.getSalarySource());
        assertTrue(job.getSalaryEstimated());
        assertEquals(6, job.getSalarySampleSize());
        assertFalse(estimator.apply(job, new SalaryEstimator.Estimate(120_000, 170_000, "USD", 6)), "unchanged → no write");

        assertTrue(estimator.apply(job, null));
        assertNull(job.getSalaryMin());
        assertNull(job.getSalarySource());
        assertFalse(job.getSalaryEstimated());

        // A legacy Gemini guess (flagged estimated, no source) is cleared when there is no data-backed estimate.
        JobPosting gemini = posting("Backend Engineer", "Seattle, WA", 900_000, 1_200_000, "INR", null);
        gemini.setSalaryEstimated(true);
        assertTrue(estimator.apply(gemini, null));
        assertNull(gemini.getSalaryMax());
    }

    @Test
    void refreshWritesOnlyChangedRows() {
        JobPostingRepository repo = mock(JobPostingRepository.class);
        JobPosting target = posting("Backend Engineer", "Austin, TX", null, null, null, null);
        JobPosting blankNoPeers = posting("Designer", "Austin, TX", null, null, null, null);
        when(repo.findWithReportedYearlySalary()).thenReturn(peers(5, "Backend Engineer", "Seattle, WA", "USD"));
        when(repo.findWithoutReportedSalary()).thenReturn(List.of(target, blankNoPeers));

        assertEquals(1, new SalaryEstimator(repo).refreshEstimates());
        verify(repo).saveAll(argThat((List<JobPosting> saved) -> saved.size() == 1 && saved.get(0) == target));
        assertEquals("ESTIMATE", target.getSalarySource());
        verify(repo, never()).saveAll(argThat((List<JobPosting> saved) -> saved.contains(blankNoPeers)));
    }

    @Test
    void medianAndRounding() {
        assertEquals(3, SalaryEstimator.median(List.of(5, 1, 3)));
        assertEquals(3, SalaryEstimator.median(List.of(1, 2, 4, 5)));
        assertEquals(124_000, SalaryEstimator.roundTo(123_600, "USD"));
        assertEquals(1_240_000, SalaryEstimator.roundTo(1_236_000, "INR"));
    }

    private static <T> T argThat(org.mockito.ArgumentMatcher<T> matcher) { return org.mockito.ArgumentMatchers.argThat(matcher); }
}
