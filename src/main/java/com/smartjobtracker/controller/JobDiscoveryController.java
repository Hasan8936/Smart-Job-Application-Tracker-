package com.smartjobtracker.controller;

import com.smartjobtracker.dto.JobDtos;
import com.smartjobtracker.jobs.discovery.DiscoveryPersonalization;
import com.smartjobtracker.jobs.discovery.JobSearch;
import com.smartjobtracker.jobs.discovery.JobSyncService;
import com.smartjobtracker.jobs.discovery.SyncProgressStore;
import com.smartjobtracker.jobs.discovery.SyncRunner;
import com.smartjobtracker.jobs.discovery.JobDiscoveryQueueService;
import com.smartjobtracker.model.JobDiscoveryTask;
import com.smartjobtracker.jobs.provider.JobProvider.JobQuery;
import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.model.JobSkill;
import com.smartjobtracker.repository.JobPostingRepository;
import com.smartjobtracker.repository.JobPostingSearchRepository;
import com.smartjobtracker.repository.JobSkillRepository;
import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.JobPreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/jobs")
public class JobDiscoveryController {
    private final JobSyncService syncService;
    private final SyncRunner syncRunner;
    private final SyncProgressStore progressStore;
    private final JobDiscoveryQueueService discoveryQueue;
    private final JobPostingRepository repository;
    private final JobPostingSearchRepository searchRepository;
    private final JobSkillRepository skillRepository;
    private final DiscoveryPersonalization personalization;
    private final UserRepository userRepository;
    private final JobPreferenceService preferences;

    /** Jobs in this country are listed first (ISO code; blank = no preference). */
    @org.springframework.beans.factory.annotation.Value("${app.job-discovery.preferred-country:IN}")
    private String preferredCountry;

    public JobDiscoveryController(JobSyncService syncService, SyncRunner syncRunner,
                                   SyncProgressStore progressStore, JobDiscoveryQueueService discoveryQueue,
                                   JobPostingRepository repository, JobPostingSearchRepository searchRepository, JobSkillRepository skillRepository,
                                   DiscoveryPersonalization personalization, UserRepository userRepository,
                                   JobPreferenceService preferences) {
        this.syncService = syncService; this.syncRunner = syncRunner; this.progressStore = progressStore; this.discoveryQueue = discoveryQueue;
        this.repository = repository; this.searchRepository = searchRepository; this.skillRepository = skillRepository;
        this.personalization = personalization; this.userRepository = userRepository;
        this.preferences = preferences;
    }

    /**
     * Starts an async job sync and returns a syncId immediately. Poll /discover/progress/{syncId} for status.
     * With no keywords and no roles, searches the user's own target/preferred roles (see DiscoveryPersonalization).
     */
    @PostMapping("/discover")
    public ResponseEntity<JobDtos.AsyncDiscoverResponse> discover(
            @Valid @RequestBody(required = false) JobDtos.DiscoverRequest request, Authentication auth) {
        JobDtos.DiscoverRequest value = request == null ? new JobDtos.DiscoverRequest(null, List.of(), List.of(), null) : request;
        String keywords = blankToNull(value.keywords());
        List<String> roles = value.roles() == null ? List.of() : value.roles().stream().filter(r -> r != null && !r.isBlank()).toList();
        if (keywords == null && roles.isEmpty()) roles = personalization.roles(currentUserId(auth));
        String syncId = UUID.randomUUID().toString();
        progressStore.init(syncId);
        syncRunner.runAsync(syncId, new JobQuery(keywords, roles, value.locations(), value.postedWithinHours()));
        return ResponseEntity.accepted().body(new JobDtos.AsyncDiscoverResponse(syncId));
    }

    /** Returns the live progress of a sync started by POST /discover. */
    @GetMapping("/discover/progress/{syncId}")
    public ResponseEntity<JobDtos.SyncProgressDto> syncProgress(@PathVariable String syncId) {
        SyncProgressStore.SyncProgress p = progressStore.get(syncId);
        JobDiscoveryTask task = discoveryQueue.find(syncId).orElse(null);
        if (task != null && ("DONE".equals(task.getStatus()) || "FAILED".equals(task.getStatus()))) {
            boolean done = "DONE".equals(task.getStatus()) || "FAILED".equals(task.getStatus());
            Map<String, String> errors = task.getErrorMessage() == null ? Map.of() : Map.of("error", task.getErrorMessage());
            p = new SyncProgressStore.SyncProgress(task.getStatus().toLowerCase(Locale.ROOT), null, 0,
                    task.getTotalSaved(), done, errors, List.of());
        } else if (p == null) {
            if (task == null) return ResponseEntity.notFound().build();
            p = new SyncProgressStore.SyncProgress(task.getStatus().toLowerCase(Locale.ROOT), null, 0,
                    task.getTotalSaved(), false, Map.of(), List.of());
        }
        return ResponseEntity.ok(new JobDtos.SyncProgressDto(
                p.status(), p.currentProvider(), p.providerJobs(), p.totalSaved(), p.done(), p.errors(), p.upToDate()));
    }

    @GetMapping
    public Page<JobDtos.JobSummary> list(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) String location,
                                         @RequestParam(required = false) String employmentType,
                                         @RequestParam(required = false) String provider,
                                         @RequestParam(required = false) OffsetDateTime postedAfter,
                                         @RequestParam(required = false) OffsetDateTime postedBefore,
                                         @RequestParam(required = false) String country,
                                         @PageableDefault(size = 20, sort = "postedAt", direction = Sort.Direction.DESC) Pageable pageable,
                                         Authentication auth) {
        JobSearch.Criteria criteria = new JobSearch.Criteria(blankToNull(q), blankToNull(location), blankToNull(employmentType),
                blankToNull(provider), postedAfter, postedBefore, null, countryCode(country), countryCode(preferredCountry));
        return enrichWithMatchScore(search(criteria, pageable), auth);
    }

    /**
     * Only jobs matching the user's saved onboarding preferences (see {@link JobSearch#recommended}). An empty page
     * when no preferences are saved; the client then shows its own prompt instead of unrelated jobs.
     */
    @GetMapping("/recommended")
    public Page<JobDtos.JobSummary> recommended(@PageableDefault(size = 10) Pageable pageable, Authentication auth) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 50);
        PageRequest page = PageRequest.of(pageable.getPageNumber(), size);
        return preferences.filters(currentUserId(auth))
                .map(p -> enrichWithMatchScore(searchRepository.findAll(JobSearch.recommended(p, countryCode(preferredCountry)), page), auth))
                .orElseGet(() -> Page.empty(page));
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
                                         @RequestParam(required = false) String country,
                                         @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
                                         Authentication auth) {
        OffsetDateTime effectiveSince = since != null ? since : OffsetDateTime.now().minusDays(7);
        JobSearch.Criteria criteria = new JobSearch.Criteria(blankToNull(q), blankToNull(location), blankToNull(employmentType),
                blankToNull(provider), null, null, effectiveSince, countryCode(country), countryCode(preferredCountry));
        return enrichWithMatchScore(search(criteria, pageable), auth);
    }

    /** The page is fetched unsorted because JobSearch applies country-first, relevance, then the requested sort. */
    private Page<JobPosting> search(JobSearch.Criteria criteria, Pageable pageable) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        return searchRepository.findAll(JobSearch.spec(criteria, pageable.getSort()), PageRequest.of(pageable.getPageNumber(), size));
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

    /** Skills from the user's profile and the resume used for matching (universal resume first). */
    private Set<String> loadUserSkills(Authentication auth) {
        return personalization.skills(currentUserId(auth));
    }

    private Long currentUserId(Authentication auth) {
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByEmail(auth.getName()).map(com.smartjobtracker.model.User::getId).orElse(null);
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

    /** Two-letter ISO country code, upper-cased; blank → null; anything else is a 400. */
    private String countryCode(String value) {
        String v = blankToNull(value);
        if (v == null) return null;
        if (!v.matches("[A-Za-z]{2}"))
            throw new IllegalArgumentException("country must be a two-letter ISO code"); // → 400 via JobDiscoveryExceptionHandler
        return v.toUpperCase(Locale.ROOT);
    }
}
