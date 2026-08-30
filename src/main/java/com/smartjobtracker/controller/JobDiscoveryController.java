package com.smartjobtracker.controller;

import com.smartjobtracker.dto.JobDtos;
import com.smartjobtracker.jobs.discovery.JobSyncService;
import com.smartjobtracker.jobs.provider.JobProvider.JobQuery;
import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.repository.JobPostingRepository;
import com.smartjobtracker.repository.JobSkillRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/jobs")
public class JobDiscoveryController {
    private final JobSyncService syncService; private final JobPostingRepository repository; private final JobSkillRepository skillRepository;
    public JobDiscoveryController(JobSyncService syncService, JobPostingRepository repository, JobSkillRepository skillRepository) { this.syncService = syncService; this.repository = repository; this.skillRepository = skillRepository; }
    @PostMapping("/discover")
    public JobDtos.DiscoverResponse discover(@Valid @RequestBody(required = false) JobDtos.DiscoverRequest request) {
        JobDtos.DiscoverRequest value = request == null ? new JobDtos.DiscoverRequest(null, List.of(), List.of()) : request;
        return new JobDtos.DiscoverResponse(syncService.sync(new JobQuery(value.keywords(), value.roles(), value.locations())));
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
        return repository.findById(id).map(job -> JobDtos.JobDetail.from(job,
                skillRepository.findByJobPostingIdOrderByName(id).stream().filter(skill -> "REQUIRED".equals(skill.getRequirement())).map(com.smartjobtracker.model.JobSkill::getName).toList(),
                skillRepository.findByJobPostingIdOrderByName(id).stream().filter(skill -> "PREFERRED".equals(skill.getRequirement())).map(com.smartjobtracker.model.JobSkill::getName).toList()))
            .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
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