package com.smartjobtracker.jobs.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.config.AiMatchingConfig;
import com.smartjobtracker.dto.HybridMatchDtos;
import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.model.JobSkill;
import com.smartjobtracker.model.MatchAnalysis;
import com.smartjobtracker.model.Resume;
import com.smartjobtracker.repository.JobPostingRepository;
import com.smartjobtracker.repository.JobSkillRepository;
import com.smartjobtracker.repository.MatchAnalysisRepository;
import com.smartjobtracker.repository.ResumeRepository;
import com.smartjobtracker.service.ResumeProfileExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HybridMatchService {
    private static final double REQUIRED_WEIGHT = 2.0;
    private static final double PREFERRED_WEIGHT = 1.0;
    private final ResumeRepository resumeRepository; private final JobPostingRepository jobRepository;
    private final JobSkillRepository skillRepository; private final MatchAnalysisRepository analysisRepository;
    private final ResumeProfileExtractor profileExtractor; private final com.smartjobtracker.jobs.discovery.JobSkillExtractor skillExtractor;
    private final SemanticSimilarityProvider fallback; private final SemanticSimilarityProvider gemini;
    private final AiMatchingConfig config; private final ObjectMapper mapper;

    public HybridMatchService(ResumeRepository resumeRepository, JobPostingRepository jobRepository,
                              JobSkillRepository skillRepository, MatchAnalysisRepository analysisRepository,
                              ResumeProfileExtractor profileExtractor, com.smartjobtracker.jobs.discovery.JobSkillExtractor skillExtractor,
                              @Qualifier("fallbackSemanticSimilarityProvider") SemanticSimilarityProvider fallback,
                              @Qualifier("geminiSemanticSimilarityProvider") SemanticSimilarityProvider gemini,
                              AiMatchingConfig config, ObjectMapper mapper) {
        this.resumeRepository=resumeRepository; this.jobRepository=jobRepository; this.skillRepository=skillRepository; this.analysisRepository=analysisRepository;
        this.profileExtractor=profileExtractor; this.skillExtractor=skillExtractor; this.fallback=fallback; this.gemini=gemini; this.config=config; this.mapper=mapper;
    }

    @Transactional
    public HybridMatchDtos.Response match(Long userId, HybridMatchDtos.Request request) {
        Resume resume = resumeRepository.findById(request.resumeId()).orElseThrow(() -> new IllegalArgumentException("Resume not found"));
        if (!Objects.equals(resume.getUserId(), userId)) throw new IllegalArgumentException("Resume not found");
        JobPosting posting = request.jobId() == null ? null : jobRepository.findById(request.jobId()).orElseThrow(() -> new IllegalArgumentException("Job not found"));
        String jobText = posting == null ? requireDescription(request.jobDescriptionText()) : posting.getDescription();
        if (jobText == null || jobText.isBlank()) throw new IllegalArgumentException("Job description is unavailable");
        String sourceHash = hash(posting == null ? jobText : "job:" + posting.getId());
        String resumeText = resume.getExtractedText() == null ? "" : resume.getExtractedText();
        Set<String> resumeSkills = resumeSkills(resumeText);
        List<JobSkill> skills = posting == null ? skillExtractor.extract(null, jobText) : skillRepository.findByJobPostingIdOrderByName(posting.getId());
        Map<String, JobSkill> uniqueSkills = skills.stream().collect(Collectors.toMap(JobSkill::getNormalizedName, s -> s, (a,b) -> a, LinkedHashMap::new));
        List<String> required = uniqueSkills.values().stream().filter(s -> "REQUIRED".equals(s.getRequirement())).map(JobSkill::getNormalizedName).toList();
        List<String> preferred = uniqueSkills.values().stream().filter(s -> "PREFERRED".equals(s.getRequirement())).map(JobSkill::getNormalizedName).toList();
        List<String> matchedRequired = required.stream().filter(resumeSkills::contains).toList();
        List<String> matchedPreferred = preferred.stream().filter(resumeSkills::contains).toList();
        double skillScore = weightedScore(required, preferred, matchedRequired, matchedPreferred);
        double fallbackSemantic = fallback.similarity(resumeText, jobText).orElse(0.0);
        var aiSemantic = gemini.similarity(resumeText, jobText);
        double semantic = aiSemantic.isPresent() ? aiSemantic.getAsDouble() : fallbackSemantic;
        String semanticProvider = aiSemantic.isPresent() ? "gemini" : "fallback";
        String title = posting == null ? jobText : posting.getTitle();
        ResumeProfileExtractor.ExtractedProfile profile = profileExtractor.extract(resumeText);
        double experience = experienceScore(resumeText, jobText);
        double role = roleScore(profile.getPreferredRoles(), title, jobText);
        double overall = round(100 * (0.55 * skillScore + 0.15 * semantic + 0.15 * experience + 0.15 * role));
        HybridMatchDtos.Breakdown breakdown = new HybridMatchDtos.Breakdown(REQUIRED_WEIGHT, PREFERRED_WEIGHT, required.size(), preferred.size(), matchedRequired.size(), matchedPreferred.size(), "Overall = 55% weighted exact skills + 15% semantic similarity + 15% experience relevance + 15% role relevance.");
        List<String> strong = new ArrayList<>(); strong.addAll(display(matchedRequired, skills)); strong.addAll(display(matchedPreferred, skills));
        List<String> partial = new ArrayList<>(); if (semantic >= 0.5 && strong.isEmpty()) partial.add("Resume language overlaps with the job description"); if (experience >= 0.5) partial.add("Experience context is relevant");
        List<String> missingRequired = display(required.stream().filter(s -> !matchedRequired.contains(s)).toList(), skills);
        List<String> missingPreferred = display(preferred.stream().filter(s -> !matchedPreferred.contains(s)).toList(), skills);
        List<String> recommendations = new ArrayList<>(); missingRequired.forEach(skill -> recommendations.add("Address the required skill: " + skill)); missingPreferred.forEach(skill -> recommendations.add("Consider highlighting verified experience with: " + skill));
        if (recommendations.isEmpty()) recommendations.add("Keep the resume focused on the verified experience and skills already present.");
        try {
            MatchAnalysis analysis = analysisRepository.findByUserIdAndResumeIdAndSourceHash(userId, resume.getId(), sourceHash).orElseGet(MatchAnalysis::new);
            analysis.setUserId(userId); analysis.setResumeId(resume.getId()); analysis.setJobPostingId(posting == null ? null : posting.getId()); analysis.setSourceHash(sourceHash); analysis.setOverallScore(overall); analysis.setSkillScore(round(100*skillScore)); analysis.setExperienceScore(round(100*experience)); analysis.setRoleScore(round(100*role)); analysis.setSemanticScore(round(100*semantic)); analysis.setBreakdownJson(mapper.writeValueAsString(breakdown)); analysisRepository.save(analysis);
        } catch (Exception ex) { throw new IllegalStateException("Could not store match analysis", ex); }
        return new HybridMatchDtos.Response(overall, round(100*skillScore), round(100*experience), round(100*role), round(100*semantic), semanticProvider, missingRequired, missingPreferred, strong, partial, recommendations, breakdown, java.time.OffsetDateTime.now());
    }

    private String requireDescription(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Job description is required"); return value; }
    private Set<String> resumeSkills(String text) { var p=profileExtractor.extract(text); Set<String> result=new HashSet<>(); for(String s:p.getSkills())result.add(s.toLowerCase(Locale.ROOT)); for(String s:p.getProgrammingLanguages())result.add(s.toLowerCase(Locale.ROOT)); for(String s:p.getFrameworks())result.add(s.toLowerCase(Locale.ROOT)); return result; }
    private double weightedScore(List<String> required,List<String> preferred,List<String> mr,List<String> mp){double total=required.size()*REQUIRED_WEIGHT+preferred.size()*PREFERRED_WEIGHT; return total==0?0:(mr.size()*REQUIRED_WEIGHT+mp.size()*PREFERRED_WEIGHT)/total;}
    private double experienceScore(String resume,String job){ boolean resumeEvidence=resume.matches("(?is).*\\b(19|20)\\d{2}\\b.*")||resume.matches("(?is).*\\b(experience|worked|intern|years)\\b.*"); boolean jobEvidence=job.matches("(?is).*\\b(experience|years|senior|lead|intern)\\b.*"); return !jobEvidence?0.5:(resumeEvidence?1.0:0.0); }
    private double roleScore(List<String> roles,String title,String job){if(title==null)return 0; String lower=(title+" "+job).toLowerCase(Locale.ROOT); return roles.stream().anyMatch(r->lower.contains(r.toLowerCase(Locale.ROOT)))?1.0:(lower.contains("engineer")||lower.contains("developer")?0.5:0.0);}
    private List<String> display(List<String> normalized,List<JobSkill> skills){return normalized.stream().map(n->skills.stream().filter(s->n.equals(s.getNormalizedName())).map(JobSkill::getName).findFirst().orElse(n)).toList();}
    private double round(double value){return Math.round(value*100.0)/100.0;}
    private String hash(String value){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format("%02x",x));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}