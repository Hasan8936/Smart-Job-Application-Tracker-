package com.smartjobtracker.service;

import com.smartjobtracker.dto.ApplicationPreparationDtos;
import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ResumeApplicationPreparationService {
    private final ApplicationProfileRepository profiles; private final ApplicationPreparationRepository preparations;
    private final ApplicationFieldMappingRepository mappings; private final ApplicationSuggestionRepository suggestions;
    private final ResumeRepository resumes; private final UserRepository users; private final CandidateProfileRepository candidateProfiles;
    private final ApplicationPreparationProvider provider;
    private final ApplicationPreparationProvider geminiProvider;
    private final com.smartjobtracker.config.AiMatchingConfig aiConfig;

    public ResumeApplicationPreparationService(ApplicationProfileRepository profiles, ApplicationPreparationRepository preparations,
            ApplicationFieldMappingRepository mappings, ApplicationSuggestionRepository suggestions, ResumeRepository resumes, UserRepository users,
            CandidateProfileRepository candidateProfiles, @Qualifier("ruleBasedApplicationPreparationProvider") ApplicationPreparationProvider provider,
            @Qualifier("geminiApplicationPreparationProvider") ApplicationPreparationProvider geminiProvider,
            com.smartjobtracker.config.AiMatchingConfig aiConfig) {
        this.profiles=profiles; this.preparations=preparations; this.mappings=mappings; this.suggestions=suggestions; this.resumes=resumes; this.users=users; this.candidateProfiles=candidateProfiles; this.provider=provider; this.geminiProvider=geminiProvider; this.aiConfig=aiConfig;
    }

    @Transactional
    public ApplicationPreparationDtos.Profile saveProfile(Long userId, ApplicationPreparationDtos.ProfileRequest request) {
        User user=users.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        ApplicationProfile profile=profiles.findByUserId(userId).orElseGet(ApplicationProfile::new); profile.setUserId(userId);
        profile.setFullName(blank(request.fullName()) ? user.getName() : request.fullName()); profile.setEmail(blank(request.email()) ? user.getEmail() : request.email());
        profile.setPhone(request.phone()); profile.setEducation(request.education()); profile.setExperience(request.experience()); profile.setSkills(request.skills());
        profile.setGithubUrl(request.githubUrl()); profile.setLinkedinUrl(request.linkedinUrl()); profile.setPortfolioUrl(request.portfolioUrl()); profile.setResumeId(request.resumeId()); profile.setUpdatedAt(OffsetDateTime.now());
        return toProfile(profiles.save(profile));
    }

    @Transactional
    public ApplicationPreparationDtos.Preparation prepare(Long userId, ApplicationPreparationDtos.PrepareRequest request) {
        Resume resume=resumes.findById(request.resumeId()).filter(r -> Objects.equals(r.getUserId(), userId)).orElseThrow(() -> new IllegalArgumentException("Resume not found"));
        ApplicationPreparation preparation=new ApplicationPreparation(); preparation.setUserId(userId); preparation.setJobDescription(request.jobDescription()); preparation=preparations.save(preparation);
        List<ApplicationPreparationProvider.FieldRequest> fields=request.mappings().stream().map(m -> new ApplicationPreparationProvider.FieldRequest(m.externalField(), m.fieldType())).toList();
        List<ApplicationFieldMapping> storedMappings=new ArrayList<>(); for (ApplicationPreparationDtos.MappingRequest requestMapping:request.mappings()) { ApplicationFieldMapping mapping=new ApplicationFieldMapping(); mapping.setPreparationId(preparation.getId()); mapping.setExternalField(requestMapping.externalField()); mapping.setFieldType(requestMapping.fieldType()); storedMappings.add(mappings.save(mapping)); }
        ApplicationPreparationProvider.FactProfile facts=facts(userId, resume); List<ApplicationPreparationProvider.Proposal> proposals;
        try { proposals=("gemini".equalsIgnoreCase(aiConfig.getProvider()) ? geminiProvider : provider).suggest(request.jobDescription(), facts, fields); }
        catch (RuntimeException ex) { proposals=provider.suggest(request.jobDescription(), facts, fields); }
        for (ApplicationPreparationProvider.Proposal proposal:proposals) {
            ApplicationFieldMapping mapping=storedMappings.stream().filter(item -> item.getExternalField().equals(proposal.externalField())).findFirst().orElse(null); if(mapping==null || !grounded(proposal, facts)) continue;
            ApplicationSuggestion suggestion=new ApplicationSuggestion(); suggestion.setPreparationId(preparation.getId()); suggestion.setMappingId(mapping.getId()); suggestion.setFieldType(proposal.fieldType()); suggestion.setSuggestedValue(proposal.value()); suggestion.setSourceEvidence(proposal.evidence()); suggestion.setRationale(proposal.rationale()); suggestions.save(suggestion);
        }
        return toPreparation(preparation);
    }

    @Transactional public ApplicationPreparationDtos.Suggestion decide(Long userId, Long suggestionId, ApplicationSuggestionDecision decision) {
        ApplicationSuggestion suggestion=suggestions.findById(suggestionId).orElseThrow(() -> new IllegalArgumentException("Suggestion not found")); preparations.findByIdAndUserId(suggestion.getPreparationId(), userId).orElseThrow(() -> new IllegalArgumentException("Suggestion not found")); suggestion.setDecision(decision); return toSuggestion(suggestions.save(suggestion));
    }
    @Transactional(readOnly=true) public ApplicationPreparationDtos.Profile getProfile(Long userId) { return profiles.findByUserId(userId).map(this::toProfile).orElse(null); }
    @Transactional(readOnly=true) public ApplicationPreparationDtos.Preparation getPreparation(Long userId, Long id) { return preparations.findByIdAndUserId(id,userId).map(this::toPreparation).orElseThrow(() -> new IllegalArgumentException("Preparation not found")); }

    private ApplicationPreparationProvider.FactProfile facts(Long userId, Resume resume) {
        User user=users.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found")); ApplicationProfile profile=profiles.findByUserId(userId).orElse(null); CandidateProfile candidate=candidateProfiles.findByUserId(userId).orElse(null);
        return new ApplicationPreparationProvider.FactProfile(first(profile==null?null:profile.getFullName(),user.getName()), first(profile==null?null:profile.getEmail(),user.getEmail()), profile==null?null:profile.getPhone(), first(profile==null?null:profile.getEducation(),candidate==null?null:candidate.getEducation()), first(profile==null?null:profile.getExperience(),candidate==null?null:candidate.getExperience()), first(profile==null?null:profile.getSkills(),candidate==null?null:candidate.getSkills()), profile==null?null:profile.getGithubUrl(), profile==null?null:profile.getLinkedinUrl(), profile==null?null:profile.getPortfolioUrl(), resume.getFileName());
    }
    private boolean grounded(ApplicationPreparationProvider.Proposal proposal, ApplicationPreparationProvider.FactProfile facts) { String expected=switch(proposal.fieldType()) { case NAME->facts.name();case EMAIL->facts.email();case PHONE->facts.phone();case EDUCATION->facts.education();case EXPERIENCE->facts.experience();case SKILLS->facts.skills();case GITHUB->facts.github();case LINKEDIN->facts.linkedin();case PORTFOLIO->facts.portfolio();case RESUME->facts.resumeFileName();}; return expected!=null&&!expected.isBlank()&&expected.equals(proposal.value())&&expected.equals(proposal.evidence()); }
    private ApplicationPreparationDtos.Preparation toPreparation(ApplicationPreparation value) { List<ApplicationFieldMapping> ms=mappings.findByPreparationId(value.getId()); return new ApplicationPreparationDtos.Preparation(value.getId(),value.getJobDescription(),getProfile(value.getUserId()),ms.stream().map(m->new ApplicationPreparationDtos.Mapping(m.getId(),m.getExternalField(),m.getFieldType())).toList(),suggestions.findByPreparationIdOrderByIdAsc(value.getId()).stream().map(this::toSuggestion).toList(),true); }
    private ApplicationPreparationDtos.Suggestion toSuggestion(ApplicationSuggestion value) { ApplicationFieldMapping mapping=mappings.findById(value.getMappingId()).orElse(null); return new ApplicationPreparationDtos.Suggestion(value.getId(),value.getMappingId(),mapping==null?"":mapping.getExternalField(),value.getFieldType(),value.getSuggestedValue(),value.getSourceEvidence(),value.getRationale(),value.getDecision()); }
    private ApplicationPreparationDtos.Profile toProfile(ApplicationProfile p) { return new ApplicationPreparationDtos.Profile(p.getId(),p.getFullName(),p.getEmail(),p.getPhone(),p.getEducation(),p.getExperience(),p.getSkills(),p.getGithubUrl(),p.getLinkedinUrl(),p.getPortfolioUrl(),p.getResumeId(),p.getUpdatedAt()); }
    private String first(String value,String fallback) { return blank(value)?fallback:value; } private boolean blank(String value){return value==null||value.isBlank();}
}