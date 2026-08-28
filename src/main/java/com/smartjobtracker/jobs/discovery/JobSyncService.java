package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.jobs.provider.JobProvider;
import com.smartjobtracker.jobs.provider.JobProvider.JobQuery;
import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.model.JobProviderSync;
import com.smartjobtracker.model.JobSkill;
import com.smartjobtracker.repository.JobPostingRepository;
import com.smartjobtracker.repository.JobProviderSyncRepository;
import com.smartjobtracker.repository.JobSkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class JobSyncService {
    private static final Logger log = LoggerFactory.getLogger(JobSyncService.class);
    private final List<JobProvider> providers; private final JobNormalizer normalizer; private final JobDeduplicator deduplicator;
    private final JobPostingRepository postingRepository; private final JobProviderSyncRepository syncRepository; private final JobSkillRepository skillRepository;
    private final JobSkillExtractor skillExtractor;
    public JobSyncService(List<JobProvider> providers, JobNormalizer normalizer, JobDeduplicator deduplicator,
                          JobPostingRepository postingRepository, JobProviderSyncRepository syncRepository,
                          JobSkillRepository skillRepository, JobSkillExtractor skillExtractor) {
        this.providers = providers; this.normalizer = normalizer; this.deduplicator = deduplicator; this.postingRepository = postingRepository; this.syncRepository = syncRepository; this.skillRepository = skillRepository; this.skillExtractor = skillExtractor;
    }
    @Transactional
    public int sync(JobQuery query) {
        if (providers.stream().noneMatch(JobProvider::isEnabled)) {
            throw new IllegalStateException(
                    "No job source is enabled. Set GREENHOUSE_ENABLED, LEVER_ENABLED, ASHBY_ENABLED, or APIFY_ENABLED "
                            + "(with the matching boards/sites/token) before running discovery.");
        }
        int saved = 0;
        for (JobProvider provider : providers) {
            if (!provider.isEnabled()) continue;
            String queryKey = key(query);
            try {
                JobProvider.JobBatch batch = provider.search(query, syncRepository.findByProviderAndQueryKey(provider.id(), queryKey).map(JobProviderSync::getCursor).orElse(null));
                for (JobPosting candidate : deduplicator.deduplicate(batch.jobs().stream()
                    .filter(job -> hasRequiredFields(job))
                    .map(provider::normalize).map(normalizer::normalize).toList())) {
                    JobPosting stored = upsert(candidate);
                    skillRepository.deleteByJobPostingId(stored.getId());
                    skillRepository.saveAll(skillExtractor.extract(stored.getId(), stored.getDescription()));
                    saved++;
                }
                JobProviderSync sync = syncRepository.findByProviderAndQueryKey(provider.id(), queryKey).orElseGet(JobProviderSync::new);
                sync.setProvider(provider.id()); sync.setQueryKey(queryKey); sync.setCursor(batch.nextCursor()); sync.setStatus("SUCCESS"); sync.setLastSyncedAt(OffsetDateTime.now()); syncRepository.save(sync);
            } catch (RuntimeException ex) {
                log.warn("Job provider sync failed provider={} queryKey={}", provider.id(), queryKey, ex);
                JobProviderSync sync = syncRepository.findByProviderAndQueryKey(provider.id(), queryKey).orElseGet(JobProviderSync::new);
                sync.setProvider(provider.id()); sync.setQueryKey(queryKey); sync.setStatus("FAILED"); sync.setLastSyncedAt(OffsetDateTime.now()); syncRepository.save(sync);
            }
        }
        return saved;
    }
    private JobPosting upsert(JobPosting candidate) {
        JobPosting existing = postingRepository.findByProviderAndExternalId(candidate.getProvider(), candidate.getExternalId()).orElse(null);
        if (existing == null) existing = postingRepository.findByDedupeHash(candidate.getDedupeHash()).orElse(null);
        if (existing != null) { candidate.setId(existing.getId()); candidate.setCreatedAt(existing.getCreatedAt()); }
        return postingRepository.save(candidate);
    }
    private String key(JobQuery query) { return String.valueOf(query.keywords()) + "|" + query.roles() + "|" + query.locations(); }
    private boolean hasRequiredFields(JobProvider.ProviderJob job) {
        return job.externalId() != null && !job.externalId().isBlank()
                && job.company() != null && !job.company().isBlank()
                && job.title() != null && !job.title().isBlank()
                && job.applyUrl() != null && !job.applyUrl().isBlank();
    }
}