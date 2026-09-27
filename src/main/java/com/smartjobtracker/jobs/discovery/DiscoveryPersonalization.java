package com.smartjobtracker.jobs.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.model.CandidateProfile;
import com.smartjobtracker.repository.CandidateProfileRepository;
import com.smartjobtracker.repository.ResumeRepository;
import com.smartjobtracker.service.UniversalResumeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * What discovery knows about the user: their verified skills (for job-card match scores) and the roles to search for
 * when they sync without typing anything. Everything comes from their own resume/profile; nothing is inferred.
 */
@Service
public class DiscoveryPersonalization {
    /** Searched when the user has no target or preferred role saved. Kept short: each role is one board search. */
    static final List<String> DEFAULT_ROLES = List.of("software engineer fresher", "junior software developer", "graduate engineer trainee");
    static final int MAX_AUTO_ROLES = 3;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final CandidateProfileRepository profiles;
    private final ResumeRepository resumes;
    private final UniversalResumeService universalResumes;
    private final JobSkillExtractor skillExtractor;
    private final ObjectMapper mapper;

    public DiscoveryPersonalization(CandidateProfileRepository profiles, ResumeRepository resumes,
                                    UniversalResumeService universalResumes, JobSkillExtractor skillExtractor, ObjectMapper mapper) {
        this.profiles = profiles; this.resumes = resumes; this.universalResumes = universalResumes;
        this.skillExtractor = skillExtractor; this.mapper = mapper;
    }

    /**
     * Lower-cased skills from the candidate profile plus the dictionary skills found in the resume used for matching
     * (the universal resume, else the newest upload). Empty when the user has neither.
     */
    @Transactional(readOnly = true)
    public Set<String> skills(Long userId) {
        if (userId == null) return Set.of();
        Set<String> all = new HashSet<>();
        profiles.findByUserId(userId).ifPresent(p -> {
            List<String> profileSkills = new ArrayList<>();
            profileSkills.addAll(parse(p.getSkills()));
            profileSkills.addAll(parse(p.getProgrammingLanguages()));
            profileSkills.addAll(parse(p.getFrameworks()));
            profileSkills.forEach(s -> all.add(s.toLowerCase(Locale.ROOT).trim()));
            // "React.js" in a profile should still count for a job that lists "React".
            all.addAll(skillExtractor.extractNames(String.join("\n", profileSkills)));
        });
        Long resumeId = universalResumes.matchingResume(userId).resumeId();
        if (resumeId != null) {
            resumes.findByIdAndUserId(resumeId, userId)
                    .ifPresent(r -> all.addAll(skillExtractor.extractNames(r.getExtractedText())));
        }
        all.remove("");
        return all;
    }

    /** Up to three roles to search: the universal resume's target role, then the profile's preferred roles. */
    @Transactional(readOnly = true)
    public List<String> roles(Long userId) {
        Set<String> roles = new LinkedHashSet<>();
        if (userId != null) {
            universalResumes.get(userId)
                    .map(u -> u.resume() == null ? null : u.resume().getTargetRole())
                    .filter(r -> !r.isBlank())
                    .ifPresent(r -> roles.add(r.trim()));
            profiles.findByUserId(userId).map(CandidateProfile::getPreferredRoles).map(this::parse)
                    .ifPresent(list -> list.stream().filter(r -> r != null && !r.isBlank()).map(String::trim).forEach(roles::add));
        }
        List<String> chosen = roles.stream().filter(r -> r.length() <= 100).limit(MAX_AUTO_ROLES).toList();
        return chosen.isEmpty() ? DEFAULT_ROLES : chosen;
    }

    private List<String> parse(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return Optional.ofNullable(mapper.readValue(json, STRING_LIST)).orElse(List.of())
                    .stream().filter(s -> s != null).toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
