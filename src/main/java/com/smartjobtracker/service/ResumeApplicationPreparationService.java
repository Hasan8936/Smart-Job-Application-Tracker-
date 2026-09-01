package com.smartjobtracker.service;

import com.smartjobtracker.dto.ApplicationPreparationDtos;
import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ResumeApplicationPreparationService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?:\\+\\d{1,3}[\\s-]?)?(?:\\(?\\d{3,5}\\)?[\\s-]?)\\d{3,4}[\\s-]?\\d{3,4}");
    private static final Pattern GITHUB_PATTERN = Pattern.compile("(https?://)?(www\\.)?github\\.com/[A-Za-z0-9_-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINKEDIN_PATTERN = Pattern.compile("(https?://)?(www\\.)?linkedin\\.com/(in|pub)/[A-Za-z0-9_-]+", Pattern.CASE_INSENSITIVE);
    /** (?<!@) keeps this from matching the domain half of an email address (e.g. "gmail.com" in "user@gmail.com") as a portfolio link. */
    private static final Pattern PORTFOLIO_PATTERN = Pattern.compile("(?<!@)(https?://)?(www\\.)?[A-Za-z0-9_-]+\\.(dev|me|io|com|xyz|vercel\\.app|github\\.io)(/[A-Za-z0-9_\\-./]*)?", Pattern.CASE_INSENSITIVE);
    /** Default label + type for every field this feature can ground -- the whole standard set is always requested, nothing is manually mapped. */
    private static final List<ApplicationPreparationDtos.MappingRequest> STANDARD_FIELDS = List.of(
            new ApplicationPreparationDtos.MappingRequest("Full name", ApplicationFieldType.NAME),
            new ApplicationPreparationDtos.MappingRequest("Email", ApplicationFieldType.EMAIL),
            new ApplicationPreparationDtos.MappingRequest("Phone", ApplicationFieldType.PHONE),
            new ApplicationPreparationDtos.MappingRequest("Education", ApplicationFieldType.EDUCATION),
            new ApplicationPreparationDtos.MappingRequest("Experience", ApplicationFieldType.EXPERIENCE),
            new ApplicationPreparationDtos.MappingRequest("Skills", ApplicationFieldType.SKILLS),
            new ApplicationPreparationDtos.MappingRequest("GitHub URL", ApplicationFieldType.GITHUB),
            new ApplicationPreparationDtos.MappingRequest("LinkedIn URL", ApplicationFieldType.LINKEDIN),
            new ApplicationPreparationDtos.MappingRequest("Portfolio URL", ApplicationFieldType.PORTFOLIO),
            new ApplicationPreparationDtos.MappingRequest("Resume file", ApplicationFieldType.RESUME));

    private final ApplicationProfileRepository profiles; private final ApplicationPreparationRepository preparations;
    private final ApplicationFieldMappingRepository mappings; private final ApplicationSuggestionRepository suggestions;
    private final ResumeRepository resumes; private final UserRepository users; private final CandidateProfileRepository candidateProfiles;
    private final ApplicationPreparationProvider provider;
    private final ApplicationPreparationProvider geminiProvider;
    private final com.smartjobtracker.config.AiMatchingConfig aiConfig;
    private final ResumeProfileExtractor extractor;

    public ResumeApplicationPreparationService(ApplicationProfileRepository profiles, ApplicationPreparationRepository preparations,
            ApplicationFieldMappingRepository mappings, ApplicationSuggestionRepository suggestions, ResumeRepository resumes, UserRepository users,
            CandidateProfileRepository candidateProfiles, @Qualifier("ruleBasedApplicationPreparationProvider") ApplicationPreparationProvider provider,
            @Qualifier("geminiApplicationPreparationProvider") ApplicationPreparationProvider geminiProvider,
            com.smartjobtracker.config.AiMatchingConfig aiConfig, ResumeProfileExtractor extractor) {
        this.profiles=profiles; this.preparations=preparations; this.mappings=mappings; this.suggestions=suggestions; this.resumes=resumes; this.users=users; this.candidateProfiles=candidateProfiles; this.provider=provider; this.geminiProvider=geminiProvider; this.aiConfig=aiConfig; this.extractor=extractor;
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
        List<ApplicationPreparationProvider.FieldRequest> fields=STANDARD_FIELDS.stream().map(m -> new ApplicationPreparationProvider.FieldRequest(m.externalField(), m.fieldType())).toList();
        List<ApplicationFieldMapping> storedMappings=new ArrayList<>(); for (ApplicationPreparationDtos.MappingRequest requestMapping:STANDARD_FIELDS) { ApplicationFieldMapping mapping=new ApplicationFieldMapping(); mapping.setPreparationId(preparation.getId()); mapping.setExternalField(requestMapping.externalField()); mapping.setFieldType(requestMapping.fieldType()); storedMappings.add(mappings.save(mapping)); }
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

    /**
     * Everything is derived straight from the resume text (plus the account's name/email as a
     * last resort) -- no manually-filled profile is required. If the user previously saved an
     * ApplicationProfile/CandidateProfile (legacy manual entry, still supported but no longer
     * needed), its values are preferred as an explicit override.
     */
    private ApplicationPreparationProvider.FactProfile facts(Long userId, Resume resume) {
        User user=users.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        ApplicationProfile profile=profiles.findByUserId(userId).orElse(null);
        CandidateProfile candidate=candidateProfiles.findByUserId(userId).orElse(null);
        String text = resume.getExtractedText() == null ? "" : resume.getExtractedText();
        ResumeProfileExtractor.ExtractedProfile extracted = extractor.extract(text);
        String skillsJoined = String.join(", ", distinct(extracted.getSkills(), extracted.getProgrammingLanguages(), extracted.getFrameworks()));
        String educationJoined = String.join("\n", extracted.getEducation());
        String experienceJoined = String.join("\n", extracted.getExperience());
        return new ApplicationPreparationProvider.FactProfile(
                first(profile==null?null:profile.getFullName(), user.getName()),
                first(profile==null?null:profile.getEmail(), firstMatch(text, EMAIL_PATTERN), user.getEmail()),
                first(profile==null?null:profile.getPhone(), firstMatch(text, PHONE_PATTERN)),
                first(profile==null?null:profile.getEducation(), candidate==null?null:candidate.getEducation(), blankToNull(educationJoined)),
                first(profile==null?null:profile.getExperience(), candidate==null?null:candidate.getExperience(), blankToNull(experienceJoined)),
                first(profile==null?null:profile.getSkills(), candidate==null?null:candidate.getSkills(), blankToNull(skillsJoined)),
                first(profile==null?null:profile.getGithubUrl(), firstMatch(text, GITHUB_PATTERN)),
                first(profile==null?null:profile.getLinkedinUrl(), firstMatch(text, LINKEDIN_PATTERN)),
                first(profile==null?null:profile.getPortfolioUrl(), firstNonSocialUrl(text)),
                resume.getFileName());
    }

    private String firstMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group().trim() : null;
    }

    private static final Set<String> EMAIL_PROVIDER_DOMAINS = Set.of("gmail.com", "yahoo.com", "outlook.com", "hotmail.com", "icloud.com", "protonmail.com", "live.com", "aol.com");

    private String firstNonSocialUrl(String text) {
        Matcher matcher = PORTFOLIO_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group().trim();
            String lower = candidate.toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
            if (lower.contains("github.com") || lower.contains("linkedin.com") || EMAIL_PROVIDER_DOMAINS.contains(lower)) continue;
            return candidate;
        }
        return null;
    }

    @SafeVarargs
    private List<String> distinct(List<String>... lists) {
        LinkedHashSet<String> combined = new LinkedHashSet<>();
        for (List<String> list : lists) combined.addAll(list);
        return new ArrayList<>(combined);
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private boolean grounded(ApplicationPreparationProvider.Proposal proposal, ApplicationPreparationProvider.FactProfile facts) { String expected=switch(proposal.fieldType()) { case NAME->facts.name();case EMAIL->facts.email();case PHONE->facts.phone();case EDUCATION->facts.education();case EXPERIENCE->facts.experience();case SKILLS->facts.skills();case GITHUB->facts.github();case LINKEDIN->facts.linkedin();case PORTFOLIO->facts.portfolio();case RESUME->facts.resumeFileName();}; return expected!=null&&!expected.isBlank()&&expected.equals(proposal.value())&&expected.equals(proposal.evidence()); }
    private ApplicationPreparationDtos.Preparation toPreparation(ApplicationPreparation value) { List<ApplicationFieldMapping> ms=mappings.findByPreparationId(value.getId()); return new ApplicationPreparationDtos.Preparation(value.getId(),value.getJobDescription(),getProfile(value.getUserId()),ms.stream().map(m->new ApplicationPreparationDtos.Mapping(m.getId(),m.getExternalField(),m.getFieldType())).toList(),suggestions.findByPreparationIdOrderByIdAsc(value.getId()).stream().map(this::toSuggestion).toList(),true); }
    private ApplicationPreparationDtos.Suggestion toSuggestion(ApplicationSuggestion value) { ApplicationFieldMapping mapping=mappings.findById(value.getMappingId()).orElse(null); return new ApplicationPreparationDtos.Suggestion(value.getId(),value.getMappingId(),mapping==null?"":mapping.getExternalField(),value.getFieldType(),value.getSuggestedValue(),value.getSourceEvidence(),value.getRationale(),value.getDecision()); }
    private ApplicationPreparationDtos.Profile toProfile(ApplicationProfile p) { return new ApplicationPreparationDtos.Profile(p.getId(),p.getFullName(),p.getEmail(),p.getPhone(),p.getEducation(),p.getExperience(),p.getSkills(),p.getGithubUrl(),p.getLinkedinUrl(),p.getPortfolioUrl(),p.getResumeId(),p.getUpdatedAt()); }
    private String first(String... values) { for (String value : values) if (!blank(value)) return value; return null; } private boolean blank(String value){return value==null||value.isBlank();}
}