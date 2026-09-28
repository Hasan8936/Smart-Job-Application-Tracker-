package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.repository.JobPostingRepository;
import com.smartjobtracker.repository.JobPostingSearchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
class JobRecommendationsTest {
    @Autowired private JobPostingRepository postings;
    @Autowired private JobPostingSearchRepository search;

    private JobPosting save(String title, String location, String type, String workMode) {
        JobPosting p = new JobPosting();
        String id = title + "-" + System.nanoTime();
        p.setProvider("jobspy"); p.setExternalId(id); p.setDedupeHash("hash-" + id);
        p.setCompany("Acme"); p.setTitle(title); p.setLocation(location); p.setApplyUrl("https://example.test/" + id);
        p.setEmploymentType(type); p.setWorkMode(workMode); p.setCountryCode("IN");
        p.setPostedAt(OffsetDateTime.now().minusDays(1));
        return postings.saveAndFlush(p);
    }

    private JobPosting salaried(String title, int max, String period, boolean estimated) {
        JobPosting p = save(title, "Pune, India", null, null);
        p.setSalaryMax(max); p.setSalaryCurrency("INR"); p.setSalaryPeriod(period); p.setSalaryEstimated(estimated);
        return postings.saveAndFlush(p);
    }

    private List<String> titles(JobSearch.Preferences prefs) {
        return search.findAll(JobSearch.recommended(prefs, "IN"), PageRequest.of(0, 50))
                .getContent().stream().map(JobPosting::getTitle).sorted().toList();
    }

    private static JobSearch.Preferences roles(String... roles) {
        return new JobSearch.Preferences(List.of(roles), null, List.of(), List.of(), List.of(), null);
    }

    @Test
    void onlyTitlesMatchingARoleAreRecommended() {
        save("Data Analyst", "Pune, India", null, null);
        save("Customer Support Executive", "Noida, India", null, null);
        save("Java Developer", "Pune, India", null, null);

        assertEquals(List.of("Customer Support Executive", "Data Analyst"), titles(roles("data analyst", "customer care")));
    }

    @Test
    void experienceLevelDropsTitlesThatClearlyDontFit() {
        save("Data Analyst", "Pune", null, null);
        save("Senior Data Analyst", "Pune", null, null);
        save("Data Analyst Intern", "Pune", null, null);

        assertEquals(List.of("Data Analyst", "Data Analyst Intern"),
                titles(new JobSearch.Preferences(List.of("data analyst"), "FRESHER", null, null, null, null)));
        assertEquals(List.of("Data Analyst", "Senior Data Analyst"),
                titles(new JobSearch.Preferences(List.of("data analyst"), "SENIOR", null, null, null, null)));
    }

    @Test
    void locationsMatchCitySpellingsAndRemoteWhenChosen() {
        save("QA Engineer", "Bengaluru, Karnataka", null, null);
        save("QA Engineer II", "Chennai, India", null, null);
        save("QA Engineer (Remote)", "India", null, "remote");

        assertEquals(List.of("QA Engineer"),
                titles(new JobSearch.Preferences(List.of("qa"), null, List.of("Bangalore"), List.of(), List.of(), null)));
        assertEquals(List.of("QA Engineer", "QA Engineer (Remote)"),
                titles(new JobSearch.Preferences(List.of("qa"), null, List.of("Bangalore"), List.of("ONSITE", "REMOTE"), List.of(), null)));
        assertEquals(List.of("QA Engineer (Remote)"),
                titles(new JobSearch.Preferences(List.of("qa"), null, List.of(), List.of("REMOTE"), List.of(), null)));
    }

    @Test
    void jobTypeKeepsUnknownTypesExceptForInternshipsOnly() {
        save("Mechanical Engineer", "Pune", "fulltime", null);
        save("Mechanical Engineer Trainee", "Pune", null, null);
        save("Mechanical Engineering Intern", "Pune", null, null);
        save("Mechanical Design Engineer", "Pune", "contract", null);

        assertEquals(List.of("Mechanical Engineer", "Mechanical Engineer Trainee"),
                titles(new JobSearch.Preferences(List.of("mechanical engineer"), null, null, null, List.of("FULL_TIME"), null)));
        assertEquals(List.of("Mechanical Engineering Intern"),
                titles(new JobSearch.Preferences(List.of("mechanical engineer"), null, null, null, List.of("INTERNSHIP"), null)));
    }

    @Test
    void minimumSalaryHidesOnlyReportedSalariesBelowIt() {
        salaried("Security Analyst", 300_000, "YEAR", false);
        salaried("Security Analyst L2", 900_000, "YEAR", false);
        salaried("Security Analyst Monthly", 20_000, "MONTH", false);
        salaried("Security Analyst Estimated", 300_000, "YEAR", true);
        save("Security Analyst Unknown", "Pune", null, null);

        assertEquals(List.of("Security Analyst Estimated", "Security Analyst L2", "Security Analyst Unknown"),
                titles(new JobSearch.Preferences(List.of("security"), null, null, null, null, 6)));
    }
}
