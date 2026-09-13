package com.smartjobtracker.controller;

import com.smartjobtracker.dto.JobDtos;
import com.smartjobtracker.jobs.discovery.JobSyncService;
import com.smartjobtracker.jobs.discovery.SyncProgressStore;
import com.smartjobtracker.jobs.discovery.SyncRunner;
import com.smartjobtracker.jobs.provider.JobProvider.JobQuery;
import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.repository.JobPostingRepository;
import com.smartjobtracker.repository.JobSkillRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
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

    public JobDiscoveryController(JobSyncService syncService, SyncRunner syncRunner,
                                   SyncProgressStore progressStore,
                                   JobPostingRepository repository, JobSkillRepository skillRepository) {
        this.syncService = syncService; this.syncRunner = syncRunner; this.progressStore = progressStore;
        this.repository = repository; this.skillRepository = skillRepository;
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
                                         @PageableDefault(size = 20, sort = "postedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return repository.search(blankToNull(q), blankToNull(location), blankToNull(employmentType), blankToNull(provider), postedAfter, postedBefore, pageable).map(JobDtos.JobSummary::from);
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
    /**
     * Jobs first synced into our database after {@code since} (defaults to 7 days ago if omitted),
     * for a "New" badge/list on the Discovery page. Filters by createdAt (when WE first saw the
     * posting), not postedAt (the posting's own listed date), so a job posted long ago that only
     * just appeared in a source we started polling still counts as "new" to this user.
     */
    @GetMapping("/new")
    public Page<JobDtos.JobSummary> listNew(@RequestParam(required = false) OffsetDateTime since,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String location,
                                         @RequestParam(required = false) String employmentType,
                                         @RequestParam(required = false) String provider,
                                         @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        OffsetDateTime effectiveSince = since != null ? since : OffsetDateTime.now().minusDays(7);
        return repository.findNewSince(effectiveSince, blankToNull(q), blankToNull(location), blankToNull(employmentType), blankToNull(provider), pageable)
                .map(JobDtos.JobSummary::from);
    }
        private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}