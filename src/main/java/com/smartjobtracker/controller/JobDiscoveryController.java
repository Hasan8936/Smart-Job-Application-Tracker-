package com.smartjobtracker.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.JobDtos;
import com.smartjobtracker.jobs.discovery.JobSyncService;
import com.smartjobtracker.jobs.discovery.SyncProgressStore;
import com.smartjobtracker.jobs.discovery.SyncRunner;
import com.smartjobtracker.jobs.provider.JobProvider.JobQuery;
import com.smartjobtracker.model.CandidateProfile;
import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.model.JobSkill;
import com.smartjobtracker.repository.CandidateProfileRepository;
import com.smartjobtracker.repository.JobPostingRepository;
import com.smartjobtracker.repository.JobSkillRepository;
import com.smartjobtracker.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/jobs")
public class JobDiscoveryController {
    private final JobSyncService syncService;
    private final SyncRunner syncRunner;
    private final SyncProgressStore progressStore;
    private final JobPostingRepository repository;
    private final JobSkillRepository skillRepository;
    private final CandidateProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    public JobDiscoveryController(JobSyncService syncService, SyncRunner syncRunner,
                                   SyncProgressStore progressStore,
                                   JobPostingRepository repository, JobSkillRepository skillRepository,
                                   CandidateProfileRepository profileRepository,
                                   UserRepository userRepository, ObjectMapper objectMapper) {
        this.syncService = syncService; this.syncRunner = syncRunner; this.progressStore = progressStore;
        this.repository = repository; this.skillRepository = skillRepository;
        this.profileRepository = profileRepository; this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /** Starts an async job sync and returns a syncId immediately. Poll /discover/progress/{syncId} for status. */
    @PostMapping("/discover")
    public ResponseEntity<JobDtos.AsyncDiscoverResponse> discover(
            @Valid @RequestBody(required = false) JobDtos.DiscoverRequest request) {
        JobDtos.DiscoverRequest value = request == null ? new JobDtos.DiscoverRequest(null, List.of(), List.of()) : request;
        String syncId = UUID.randomUUID().toString();
        progressStore.init(syncId);
        syncRunner.runAsync(syncId, new JobQuery(value.keywords(), value.roles(), value.locations()));
        return ResponseEntity.accepted().body(new JobDtos.AsyncDiscoverResponse(syncId));
    }

    /** Returns the live progress of a sync started by POST /discover. */
    @GetMapping("/discover/progress/{syncId}")
    public ResponseEntity<JobDtos.SyncProgressDto> syncProgress(@PathVariable String syncId) {
        SyncProgressStore.SyncProgress p = progressStore.get(syncId);
        if (p == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new JobDtos.SyncProgressDto(
                p.status(), p.currentProvider(), p.providerJobs(), p.totalSaved(), p.done(), p.errors()));
    }

    @GetMapping
    public Page<JobDtos.JobSummary> list(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) String location,
                                         @RequestParam(required = false) String employmentType,
                                         @RequestParam(required = false) String provider,
                                         @RequestParam(required = false) OffsetDateTime postedAfter,
                                         @RequestParam(required = false) OffsetDateTime postedBefore,
                                         @PageableDefault(size = 20, sort = "postedAt", direction = Sort.Direction.DESC) Pageable pageable,
                                         Authentication auth) {
        Page<JobPosting> page = repository.search(blankToNull(q), blankToNull(location), blankToNull(employmentType), blankToNull(provider), postedAfter, postedBefore, pageable);
        return enrichWithMatchScore(page, auth);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobDtos.JobDetail> detail(@PathVariable Long id) {
        return repository.findById(id).map(job -> {
            var skills = skillRepository.findByJobPostingIdOrderByName(id);
            return JobDtos.JobDetail.from(job,
                skills.stream().filter(s -> "REQUIRED".equals(s.getRequirement())).map(com.smartjobtracker.model.JobSkill::getName).toList(),
                skills.stream().filter(s -> "PREFERRED".equals(s.getRequirement())).map(com.smartjobtracker.model.JobSkill::getName).toList());
        }).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/new")
    public Page<JobDtos.JobSummary> listNew(@RequestParam(required = false) OffsetDateTime since,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String location,
                                         @RequestParam(required = false) String employmentType,
                                         @RequestParam(required = false) String provider,
                                         @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
                                         Authentication auth) {
        OffsetDateTime effectiveSince = since != null ? since : OffsetDateTime.now().minusDays(7);
        Page<JobPosting> page = repository.findNewSince(effectiveSince, blankToNull(q), blankToNull(location), blankToNull(employmentType), blankToNull(provider), pageable);
        return enrichWithMatchScore(page, auth);
    }

    private Page<JobDtos.JobSummary> enrichWithMatchScore(Page<JobPosting> page, Authentication auth) {
        List<Long> ids = page.getContent().stream().map(JobPosting::getId).toList();
        Map<Long, List<JobSkill>> skillsByJob = ids.isEmpty() ? Map.of() :
            skillRepository.findByJobPostingIdIn(ids).stream()
                .collect(Collectors.groupingBy(JobSkill::getJobPostingId));

        Set<String> userSkills = loadUserSkills(auth);

        return page.map(job -> {
            List<JobSkill> jobSkills = skillsByJob.getOrDefault(job.getId(), List.of());
            List<String> skillNames = jobSkills.stream()
                .filter(s -> "REQUIRED".equals(s.getRequirement()))
                .map(JobSkill::getName)
                .toList();
            Integer score = computeMatchScore(jobSkills, userSkills);
            return JobDtos.JobSummary.from(job, score, skillNames);
        });
    }

    private Set<String> loadUserSkills(Authentication auth) {
        if (auth == null) return Set.of();
        return userRepository.findByEmail(auth.getName())
            .flatMap(u -> profileRepository.findByUserId(u.getId()))
            .map(this::parseProfileSkills)
            .orElse(Set.of());
    }

    private Set<String> parseProfileSkills(CandidateProfile p) {
        Set<String> all = new HashSet<>();
        addParsed(p.getSkills(), all);
        addParsed(p.getProgrammingLanguages(), all);
        addParsed(p.getFrameworks(), all);
        return all;
    }

    private void addParsed(String json, Set<String> target) {
        if (json == null || json.isBlank()) return;
        try {
            objectMapper.readValue(json, STRING_LIST)
                .forEach(s -> target.add(s.toLowerCase(Locale.ROOT).trim()));
        } catch (Exception ignored) {}
    }

    private Integer computeMatchScore(List<JobSkill> jobSkills, Set<String> userSkills) {
        if (userSkills.isEmpty() || jobSkills.isEmpty()) return null;
        List<String> required = jobSkills.stream()
            .filter(s -> "REQUIRED".equals(s.getRequirement()))
            .map(s -> s.getNormalizedName().toLowerCase(Locale.ROOT))
            .toList();
        List<String> preferred = jobSkills.stream()
            .filter(s -> "PREFERRED".equals(s.getRequirement()))
            .map(s -> s.getNormalizedName().toLowerCase(Locale.ROOT))
            .toList();
        if (required.isEmpty() && preferred.isEmpty()) return null;
        long matchedReq = required.stream().filter(userSkills::contains).count();
        long matchedPref = preferred.stream().filter(userSkills::contains).count();
        double score;
        if (!required.isEmpty() && !preferred.isEmpty()) {
            score = (double) matchedReq / required.size() * 70.0
                  + (double) matchedPref / preferred.size() * 30.0;
        } else if (!required.isEmpty()) {
            score = (double) matchedReq / required.size() * 100.0;
        } else {
            score = (double) matchedPref / preferred.size() * 100.0;
        }
        return (int) Math.round(score);
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
