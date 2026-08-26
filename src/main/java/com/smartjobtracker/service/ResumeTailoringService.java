package com.smartjobtracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.ResumeTailoringDtos;
import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ResumeTailoringService {
    private static final TypeReference<List<Long>> IDS = new TypeReference<>() {};
    private final ResumeRepository resumes;
    private final ResumeProfileExtractor extractor;
    private final TailoringSessionRepository sessions;
    private final TailoringSuggestionRepository suggestions;
    private final ResumeVersionRepository versions;
    private final ObjectMapper mapper;
    private final ResumeTailoringProvider fallback;
    private final ResumeTailoringProvider gemini;
    private final com.smartjobtracker.jobs.discovery.JobSkillExtractor skillExtractor;
    private final com.smartjobtracker.config.AiMatchingConfig aiConfig;

    public ResumeTailoringService(ResumeRepository resumes, ResumeProfileExtractor extractor, TailoringSessionRepository sessions,
                                  TailoringSuggestionRepository suggestions, ResumeVersionRepository versions, ObjectMapper mapper,
                                  @Qualifier("ruleBasedResumeTailoringProvider") ResumeTailoringProvider fallback,
                                  @Qualifier("geminiResumeTailoringProvider") ResumeTailoringProvider gemini,
                                  com.smartjobtracker.jobs.discovery.JobSkillExtractor skillExtractor,
                                  com.smartjobtracker.config.AiMatchingConfig aiConfig) {
        this.resumes = resumes; this.extractor = extractor; this.sessions = sessions; this.suggestions = suggestions; this.versions = versions;
        this.mapper = mapper; this.fallback = fallback; this.gemini = gemini; this.skillExtractor = skillExtractor; this.aiConfig = aiConfig;
    }

    @Transactional
    public ResumeTailoringDtos.Analysis analyze(Long userId, ResumeTailoringDtos.AnalyzeRequest request) {
        Resume resume = resumes.findById(request.resumeId()).filter(item -> Objects.equals(item.getUserId(), userId)).orElseThrow(() -> new IllegalArgumentException("Resume not found"));
        String resumeText = resume.getExtractedText() == null ? "" : resume.getExtractedText();
        List<String> atsKeywords = skillExtractor.extract(null, request.jobDescription()).stream().map(com.smartjobtracker.model.JobSkill::getName).distinct().toList();
        ResumeProfileExtractor.ExtractedProfile profile = extractor.extract(resumeText);
        TailoringSession session = new TailoringSession(); session.setUserId(userId); session.setSourceResumeId(resume.getId()); session.setJobDescription(request.jobDescription());
        session = sessions.save(session);
        List<ResumeTailoringProvider.Proposal> proposals;
        try { proposals = "gemini".equalsIgnoreCase(aiConfig.getProvider()) ? gemini.suggest(resumeText, request.jobDescription(), atsKeywords) : fallback.suggest(resumeText, request.jobDescription(), atsKeywords); }
        catch (RuntimeException ex) { proposals = fallback.suggest(resumeText, request.jobDescription(), atsKeywords); }
        for (ResumeTailoringProvider.Proposal proposal : proposals) {
            if (!grounded(proposal, resumeText)) continue;
            TailoringSuggestion suggestion = new TailoringSuggestion(); suggestion.setSessionId(session.getId()); suggestion.setCategory(proposal.category()); suggestion.setBeforeText(proposal.beforeText()); suggestion.setAfterText(proposal.afterText()); suggestion.setRationale(proposal.rationale()); suggestion.setEvidenceText(proposal.evidenceText()); suggestions.save(suggestion);
        }
        return analysis(session, profile, atsKeywords);
    }

    @Transactional
    public ResumeTailoringDtos.Suggestion decide(Long userId, Long suggestionId, TailoringSuggestionDecision decision) {
        TailoringSuggestion suggestion = suggestions.findById(suggestionId).orElseThrow(() -> new IllegalArgumentException("Suggestion not found"));
        TailoringSession session = sessions.findByIdAndUserId(suggestion.getSessionId(), userId).orElseThrow(() -> new IllegalArgumentException("Suggestion not found"));
        suggestion.setDecision(decision); return toSuggestion(suggestions.save(suggestion));
    }

    @Transactional
    public ResumeTailoringDtos.Version createVersion(Long userId, Long sessionId) {
        TailoringSession session = sessions.findByIdAndUserId(sessionId, userId).orElseThrow(() -> new IllegalArgumentException("Tailoring session not found"));
        Resume resume = resumes.findById(session.getSourceResumeId()).filter(item -> Objects.equals(item.getUserId(), userId)).orElseThrow(() -> new IllegalArgumentException("Resume not found"));
        List<TailoringSuggestion> accepted = suggestions.findBySessionIdOrderByIdAsc(sessionId).stream().filter(item -> item.getDecision() == TailoringSuggestionDecision.ACCEPTED).toList();
        String content = resume.getExtractedText() == null ? "" : resume.getExtractedText();
        for (TailoringSuggestion suggestion : accepted) content = content.replace(suggestion.getBeforeText(), suggestion.getAfterText());
        ResumeVersion version = new ResumeVersion(); version.setUserId(userId); version.setSourceResumeId(resume.getId()); version.setTailoringSessionId(sessionId); version.setJobDescription(session.getJobDescription()); version.setContent(content); version.setAcceptedSuggestionIds(toJson(accepted.stream().map(TailoringSuggestion::getId).toList()));
        return toVersion(versions.save(version));
    }

    @Transactional(readOnly = true)
    public List<ResumeTailoringDtos.Version> versions(Long userId) { return versions.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toVersion).toList(); }

    private boolean grounded(ResumeTailoringProvider.Proposal proposal, String source) {
        return nonBlank(proposal.beforeText()) && source.contains(proposal.beforeText()) && source.contains(proposal.evidenceText()) && nonBlank(proposal.afterText()) && allWordsFromSource(proposal.afterText(), source);
    }
    private boolean allWordsFromSource(String candidate, String source) { Set<String> words = Arrays.stream(source.toLowerCase().split("[^a-z0-9+#.]+" )).filter(word -> !word.isBlank()).collect(Collectors.toSet()); return Arrays.stream(candidate.toLowerCase().split("[^a-z0-9+#.]+" )).filter(word -> !word.isBlank()).allMatch(words::contains); }
    private ResumeTailoringDtos.Analysis analysis(TailoringSession session, ResumeProfileExtractor.ExtractedProfile profile, List<String> keywords) { return new ResumeTailoringDtos.Analysis(session.getId(), session.getSourceResumeId(), keywords, profile.getSkills(), profile.getProjects(), suggestions.findBySessionIdOrderByIdAsc(session.getId()).stream().map(this::toSuggestion).toList(), session.getCreatedAt()); }
    private ResumeTailoringDtos.Suggestion toSuggestion(TailoringSuggestion value) { return new ResumeTailoringDtos.Suggestion(value.getId(), value.getCategory(), value.getBeforeText(), value.getAfterText(), value.getRationale(), value.getEvidenceText(), value.getDecision()); }
    private ResumeTailoringDtos.Version toVersion(ResumeVersion value) { return new ResumeTailoringDtos.Version(value.getId(), value.getSourceResumeId(), value.getTailoringSessionId(), value.getJobDescription(), value.getContent(), fromJson(value.getAcceptedSuggestionIds()), value.getCreatedAt()); }
    private String toJson(List<Long> values) { try { return mapper.writeValueAsString(values); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private List<Long> fromJson(String value) { try { return mapper.readValue(value, IDS); } catch (Exception ex) { return List.of(); } }
    private boolean nonBlank(String value) { return value != null && !value.isBlank(); }
}