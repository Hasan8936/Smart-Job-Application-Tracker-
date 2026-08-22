package com.smartjobtracker.service;

import com.smartjobtracker.dto.MatchResponse;
import com.smartjobtracker.model.Resume;
import com.smartjobtracker.repository.ResumeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class KeywordMatchServiceTest {

    @Autowired
    private KeywordMatchService keywordMatchService;

    @Autowired
    private ResumeRepository resumeRepository;

    @Test
    public void score_should_match_skills_from_resume() {
        Resume r = new Resume();
        r.setUserId(1L);
        r.setFileName("test.txt");
        r.setExtractedText("Java Spring Boot SQL Docker AWS");
        resumeRepository.save(r);

        String jd = "Looking for a Java Spring Boot developer with SQL and Docker experience";
        MatchResponse res = keywordMatchService.score(r.getId(), jd);

        assertThat(res.getMatchScore()).isGreaterThan(0);
        assertThat(res.getMatchedKeywords()).contains("java", "spring boot", "sql", "docker");
    }
}
