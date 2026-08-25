package com.smartjobtracker.jobs.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.config.AiMatchingConfig;
import com.smartjobtracker.dto.HybridMatchDtos;
import com.smartjobtracker.jobs.discovery.JobSkillExtractor;
import com.smartjobtracker.model.JobSkill;
import com.smartjobtracker.model.MatchAnalysis;
import com.smartjobtracker.model.Resume;
import com.smartjobtracker.repository.JobPostingRepository;
import com.smartjobtracker.repository.JobSkillRepository;
import com.smartjobtracker.repository.MatchAnalysisRepository;
import com.smartjobtracker.repository.ResumeRepository;
import com.smartjobtracker.service.ResumeProfileExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HybridMatchServiceTest {
    @Test
    void weightsRequiredSkillsMoreThanPreferredAndUsesFallbackSemanticScore() {
        Resume resume = new Resume(); resume.setId(4L); resume.setUserId(9L); resume.setExtractedText("Java developer with 5 years experience");
        ResumeRepository resumes = mock(ResumeRepository.class); when(resumes.findById(4L)).thenReturn(Optional.of(resume));
        JobSkill required = skill("Java", "REQUIRED"); JobSkill preferred = skill("React", "PREFERRED");
        JobSkillExtractor skills = mock(JobSkillExtractor.class); when(skills.extract(isNull(), any())).thenReturn(List.of(required, preferred));
        ResumeProfileExtractor profiles = mock(ResumeProfileExtractor.class);
        when(profiles.extract(any())).thenReturn(new ResumeProfileExtractor.ExtractedProfile(List.of(), List.of("Java"), List.of(), List.of(), List.of(), List.of(), List.of()));
        SemanticSimilarityProvider fallback = (a, b) -> OptionalDouble.of(0.5);
        SemanticSimilarityProvider gemini = (a, b) -> OptionalDouble.empty();
        MatchAnalysisRepository analyses = mock(MatchAnalysisRepository.class); when(analyses.findByUserIdAndResumeIdAndSourceHash(eq(9L), eq(4L), any())).thenReturn(Optional.empty());
        HybridMatchService service = new HybridMatchService(resumes, mock(JobPostingRepository.class), mock(JobSkillRepository.class), analyses,
                profiles, skills, fallback, gemini, new AiMatchingConfig(), new ObjectMapper());

        HybridMatchDtos.Response result = service.match(9L, new HybridMatchDtos.Request(4L, null, "Required Java. React is preferred."));

        assertEquals(66.67, result.skillMatch(), 0.01);
        assertEquals(50.0, result.semanticSimilarity(), 0.01);
        assertEquals(51.67, result.overallMatch(), 0.01);
        assertEquals("fallback", result.semanticProvider());
        verify(analyses).save(any(MatchAnalysis.class));
    }

    private JobSkill skill(String name, String requirement) {
        JobSkill skill = new JobSkill(); skill.setName(name); skill.setNormalizedName(name.toLowerCase()); skill.setRequirement(requirement); return skill;
    }
}