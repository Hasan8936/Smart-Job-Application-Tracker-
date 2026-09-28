package com.smartjobtracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.JobPreferencesDto;
import com.smartjobtracker.jobs.discovery.JobSearch;
import com.smartjobtracker.model.JobSearchPreference;
import com.smartjobtracker.repository.JobSearchPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Reads and saves the onboarding job preferences that drive "Recommended jobs" and the default Sync roles. */
@Service
public class JobPreferenceService {
    private static final Logger log = LoggerFactory.getLogger(JobPreferenceService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JobSearchPreferenceRepository repository;
    private final ObjectMapper mapper;

    public JobPreferenceService(JobSearchPreferenceRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** Empty when the user has never answered or skipped the popup. */
    @Transactional(readOnly = true)
    public Optional<JobPreferencesDto> get(Long userId) {
        return repository.findByUserId(userId).map(this::toDto);
    }

    @Transactional
    public JobPreferencesDto save(Long userId, JobPreferencesDto input) {
        List<String> roles = clean(input.roles());
        if (roles.isEmpty()) throw new IllegalArgumentException("add at least one role");
        JobSearchPreference p = repository.findByUserId(userId).orElseGet(() -> {
            JobSearchPreference fresh = new JobSearchPreference();
            fresh.setUserId(userId);
            return fresh;
        });
        p.setStatus(JobSearchPreference.SAVED);
        p.setRoles(write(roles));
        p.setExperienceLevel(input.experienceLevel());
        p.setLocations(write(clean(input.locations())));
        p.setWorkModes(write(clean(input.workModes())));
        p.setJobTypes(write(clean(input.jobTypes())));
        p.setMinSalaryLpa(input.minSalaryLpa() == null || input.minSalaryLpa() == 0 ? null : input.minSalaryLpa());
        p.setUpdatedAt(OffsetDateTime.now());
        p = repository.save(p);
        log.info("Job preferences saved userId={} roles={} locations={}", userId, roles.size(), read(p.getLocations()).size());
        return toDto(p);
    }

    /** Records that the popup was dismissed. Never overwrites preferences that were already saved. */
    @Transactional
    public void skip(Long userId) {
        if (repository.findByUserId(userId).isPresent()) return;
        JobSearchPreference p = new JobSearchPreference();
        p.setUserId(userId);
        p.setStatus(JobSearchPreference.SKIPPED);
        repository.save(p);
        log.info("Job preferences popup skipped userId={}", userId);
    }

    /** The saved preferences as search filters; empty when not saved (skipped or never answered). */
    @Transactional(readOnly = true)
    public Optional<JobSearch.Preferences> filters(Long userId) {
        if (userId == null) return Optional.empty();
        return repository.findByUserId(userId)
                .filter(p -> JobSearchPreference.SAVED.equals(p.getStatus()))
                .map(p -> new JobSearch.Preferences(read(p.getRoles()), p.getExperienceLevel(), read(p.getLocations()),
                        read(p.getWorkModes()), read(p.getJobTypes()), p.getMinSalaryLpa()));
    }

    private JobPreferencesDto toDto(JobSearchPreference p) {
        return new JobPreferencesDto(p.getStatus(), read(p.getRoles()), p.getExperienceLevel(), read(p.getLocations()),
                read(p.getWorkModes()), read(p.getJobTypes()), p.getMinSalaryLpa(), p.getUpdatedAt());
    }

    /** Trimmed, blank-free, case-insensitively de-duplicated (first spelling wins). */
    private static List<String> clean(List<String> values) {
        if (values == null) return List.of();
        Map<String, String> unique = new LinkedHashMap<>();
        for (String v : values) {
            if (v == null || v.isBlank()) continue;
            String trimmed = v.trim().replaceAll("\\s+", " ");
            unique.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return new ArrayList<>(unique.values());
    }

    private String write(List<String> values) {
        try {
            return mapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("Could not store job preferences", e);
        }
    }

    private List<String> read(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> parsed = mapper.readValue(json, STRING_LIST);
            return parsed == null ? List.of() : parsed.stream().filter(s -> s != null && !s.isBlank()).toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
