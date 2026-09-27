package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.model.JobSkill;
import com.smartjobtracker.repository.JobPostingRepository;
import com.smartjobtracker.repository.JobPostingSearchRepository;
import com.smartjobtracker.repository.JobSkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class JobSearchTest {
    @Autowired private JobPostingRepository postings;
    @Autowired private JobPostingSearchRepository search;
    @Autowired private JobSkillRepository skills;

    private JobPosting save(String title, String company, String location, String country, Integer daysAgo, String... skillNames) {
        JobPosting p = new JobPosting();
        String id = title + "-" + System.nanoTime();
        p.setProvider("jobspy"); p.setExternalId(id); p.setDedupeHash("hash-" + id);
        p.setCompany(company); p.setTitle(title); p.setLocation(location); p.setApplyUrl("https://example.test/" + id);
        p.setCountryCode(country);
        p.setPostedAt(daysAgo == null ? null : OffsetDateTime.now().minusDays(daysAgo));
        p = postings.saveAndFlush(p);
        for (String name : skillNames) {
            JobSkill s = new JobSkill();
            s.setJobPostingId(p.getId()); s.setName(name); s.setNormalizedName(name.toLowerCase()); s.setRequirement("REQUIRED");
            skills.saveAndFlush(s);
        }
        return p;
    }

    private List<String> titles(String q, String preferredCountry) {
        Page<JobPosting> page = search.findAll(JobSearch.spec(
                new JobSearch.Criteria(q, null, null, null, null, null, null, null, preferredCountry),
                Sort.by(Sort.Direction.DESC, "postedAt")), PageRequest.of(0, 20));
        return page.getContent().stream().map(JobPosting::getTitle).toList();
    }

    @Test
    void parsesTermsWithPhrasesSynonymsAndStopWords() {
        assertEquals(List.of(), JobSearch.terms("  "));
        List<List<String>> terms = JobSearch.terms("Front End developer jobs in Bengaluru");
        assertEquals(3, terms.size());
        assertTrue(terms.get(0).contains("front end"));
        assertTrue(terms.get(1).contains("developer"));
        assertTrue(terms.get(2).containsAll(List.of("bangalore", "bengaluru")));
        assertTrue(JobSearch.terms("SDE").get(0).contains("software engineer"));
        assertEquals(List.of(List.of("c++")), JobSearch.terms("c++"));
    }

    @Test
    void everyWordMustMatchTitleCompanyLocationOrSkill() {
        save("Frontend Developer", "Acme", "Bengaluru, India", "IN", 1);
        save("React Engineer", "Globex", "Pune, India", "IN", 2, "React");
        save("Backend Developer", "Initech", "Bangalore, India", "IN", 3, "Java");
        save("Sales Manager", "Google", "Mumbai, India", "IN", 1);

        assertEquals(List.of("Frontend Developer"), titles("front-end bangalore", null));
        assertEquals(List.of("React Engineer"), titles("react.js", null), "matched through the job's skills");
        assertEquals(List.of("Backend Developer"), titles("java bangalore", null));
        assertEquals(List.of(), titles("go", null), "short words match whole words only, not 'Google'");
    }

    @Test
    void preferredCountryThenRelevanceThenNewestWithUndatedLast() {
        save("Java Developer", "Acme", "Austin", "US", 0);
        save("Platform Engineer", "Initech", "Pune", "IN", 1, "Java");
        save("Java Developer", "Globex", "Pune", "IN", 5);
        save("Senior Java Developer", "Hooli", "Delhi", "IN", null);

        List<String> ranked = titles("java developer", "IN");
        // India first; within India, titles matching both words beat a skill-only match; undated last among equals.
        assertEquals(List.of("Java Developer", "Senior Java Developer", "Platform Engineer", "Java Developer"), ranked);
        long total = search.count(JobSearch.spec(new JobSearch.Criteria("java", null, null, null, null, null, null, "IN", "IN"), Sort.unsorted()));
        assertEquals(3, total);
    }
}
