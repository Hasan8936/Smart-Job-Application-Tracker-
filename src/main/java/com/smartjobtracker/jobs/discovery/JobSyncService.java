package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.jobs.provider.JobProvider;
import com.smartjobtracker.jobs.provider.JobProvider.JobQuery;
import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.model.JobProviderSync;
import com.smartjobtracker.repository.JobPostingRepository;
import com.smartjobtracker.repository.JobProviderSyncRepository;
import com.smartjobtracker.repository.JobSkillRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pulls jobs from every enabled source. Sources are fetched in parallel (the slow, network-bound part), then each
 * batch is stored in one transaction in source order. A source that already ran the same search successfully within
 * the cool-down window is skipped, so pressing Sync twice doesn't hit the job boards twice.
 */
@Service
public class JobSyncService {
    private static final Logger log = LoggerFactory.getLogger(JobSyncService.class);
    private static final AtomicInteger THREAD_COUNT = new AtomicInteger();

    private final List<JobProvider> providers; private final JobNormalizer normalizer; private final JobDeduplicator deduplicator;
    private final JobPostingRepository postingRepository; private final JobProviderSyncRepository syncRepository; private final JobSkillRepository skillRepository;
    private final JobSkillExtractor skillExtractor;
    private final SyncProgressStore progressStore;
    private final SalaryEstimator salaryEstimator;
    private final ScriptFilter scriptFilter;
    private final TransactionTemplate transactions;
    private final int resyncCooldownMinutes;
    private final ExecutorService fetchPool;

    @Autowired
    public JobSyncService(List<JobProvider> providers, JobNormalizer normalizer, JobDeduplicator deduplicator,
                          JobPostingRepository postingRepository, JobProviderSyncRepository syncRepository,
                          JobSkillRepository skillRepository, JobSkillExtractor skillExtractor,
                          SyncProgressStore progressStore, SalaryEstimator salaryEstimator, ScriptFilter scriptFilter,
                          PlatformTransactionManager transactionManager,
                          @Value("${app.job-discovery.resync-cooldown-minutes:10}") int resyncCooldownMinutes,
                          @Value("${app.job-discovery.fetch-threads:1}") int fetchThreads) {
        this.providers = providers; this.normalizer = normalizer; this.deduplicator = deduplicator; this.postingRepository = postingRepository; this.syncRepository = syncRepository; this.skillRepository = skillRepository; this.skillExtractor = skillExtractor; this.progressStore = progressStore;
        this.salaryEstimator = salaryEstimator;
        this.scriptFilter = scriptFilter;
        this.transactions = new TransactionTemplate(transactionManager);
        this.resyncCooldownMinutes = resyncCooldownMinutes;
        int threads = Math.max(1, Math.min(fetchThreads, 4));
        this.fetchPool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "job-source-fetch-" + THREAD_COUNT.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    JobSyncService(List<JobProvider> providers, JobNormalizer normalizer, JobDeduplicator deduplicator,
                   JobPostingRepository postingRepository, JobProviderSyncRepository syncRepository,
                   JobSkillRepository skillRepository, JobSkillExtractor skillExtractor,
                   SyncProgressStore progressStore, SalaryEstimator salaryEstimator, ScriptFilter scriptFilter,
                   PlatformTransactionManager transactionManager, int resyncCooldownMinutes) {
        this(providers, normalizer, deduplicator, postingRepository, syncRepository, skillRepository, skillExtractor,
                progressStore, salaryEstimator, scriptFilter, transactionManager, resyncCooldownMinutes, 4);
    }

    @PreDestroy
    void shutdown() { fetchPool.shutdownNow(); }

    /** Synchronous sync — used by scheduled jobs and tests. No progress tracking. */
    public SyncResult sync(JobQuery query) {
        return sync(null, query);
    }

    /** Sync with optional progress tracking via syncId (null = no tracking). */
    public SyncResult sync(String syncId, JobQuery query) {
        if (providers.stream().noneMatch(JobProvider::isEnabled)) {
            throw new IllegalStateException(
                    "No job source is enabled. Set GREENHOUSE_ENABLED, LEVER_ENABLED, ASHBY_ENABLED, APIFY_ENABLED, "
                            + "or TELEGRAM_ENABLED (with the matching boards/sites/token/channels) before running discovery.");
        }
        String queryKey = key(query);
        Map<String, String> providerErrors = new LinkedHashMap<>();
        List<String> upToDate = new ArrayList<>();

        // Start every due source at once; the fetches overlap instead of running one after another.
        Map<JobProvider, CompletableFuture<JobProvider.JobBatch>> fetches = new LinkedHashMap<>();
        for (JobProvider provider : providers) {
            if (!provider.isEnabled()) continue;
            JobProviderSync previous = syncRepository.findByProviderAndQueryKey(provider.id(), queryKey).orElse(null);
            if (recentlySynced(previous)) {
                upToDate.add(provider.id());
                continue;
            }
            String cursor = previous == null ? null : previous.getCursor();
            fetches.put(provider, CompletableFuture.supplyAsync(() -> provider.search(query, cursor), fetchPool));
        }

        int saved = 0;
        for (Map.Entry<JobProvider, CompletableFuture<JobProvider.JobBatch>> fetch : fetches.entrySet()) {
            JobProvider provider = fetch.getKey();
            if (syncId != null) progressStore.update(syncId, provider.id(), 0, saved);
            try {
                JobProvider.JobBatch batch = unwrap(fetch.getValue());
                Integer stored = transactions.execute(status -> store(provider, batch));
                int providerSaved = stored == null ? 0 : stored;
                saved += providerSaved;
                if (syncId != null) progressStore.update(syncId, provider.id(), providerSaved, saved);
                record(provider.id(), queryKey, "SUCCESS", batch.nextCursor(), true);
            } catch (RuntimeException ex) {
                log.warn("Job provider sync failed provider={} queryKey={}", provider.id(), queryKey, ex);
                record(provider.id(), queryKey, "FAILED", null, false);
                providerErrors.put(provider.id(), ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            }
        }
        // New reported salaries may make new estimates possible (and re-synced rows lost their old ones).
        if (saved > 0) {
            try { salaryEstimator.refreshEstimates(); }
            catch (RuntimeException ex) { log.warn("Salary estimate refresh failed after sync", ex); }
        }
        if (!upToDate.isEmpty()) log.info("Skipped recently synced sources {} for queryKey={}", upToDate, queryKey);
        return new SyncResult(saved, providerErrors, upToDate);
    }

    public record SyncResult(int saved, Map<String, String> providerErrors, List<String> upToDate) {
        public SyncResult(int saved, Map<String, String> providerErrors) { this(saved, providerErrors, List.of()); }
    }

    /** Normalizes, filters, de-duplicates and upserts one source's batch; returns how many postings were stored. */
    private int store(JobProvider provider, JobProvider.JobBatch batch) {
        List<JobPosting> normalized = batch.jobs().stream()
                .filter(this::hasRequiredFields)
                .map(provider::normalize).map(normalizer::normalize).toList();
        List<JobPosting> readable = normalized.stream()
                .filter(job -> !scriptFilter.isBlocked(job.getTitle(), job.getDescription())).toList();
        if (readable.size() < normalized.size())
            log.info("Skipped {} posting(s) in a blocked script from provider={}", normalized.size() - readable.size(), provider.id());
        int stored = 0;
        for (JobPosting candidate : deduplicator.deduplicate(readable)) {
            JobPosting posting = upsert(candidate);
            skillRepository.deleteByJobPostingId(posting.getId());
            skillRepository.saveAll(skillExtractor.extract(posting.getId(), posting.getDescription()));
            stored++;
        }
        return stored;
    }

    private boolean recentlySynced(JobProviderSync previous) {
        return resyncCooldownMinutes > 0 && previous != null && "SUCCESS".equals(previous.getStatus())
                && previous.getLastSyncedAt() != null
                && previous.getLastSyncedAt().isAfter(OffsetDateTime.now().minusMinutes(resyncCooldownMinutes));
    }

    private void record(String providerId, String queryKey, String status, String cursor, boolean setCursor) {
        JobProviderSync sync = syncRepository.findByProviderAndQueryKey(providerId, queryKey).orElseGet(JobProviderSync::new);
        sync.setProvider(providerId); sync.setQueryKey(queryKey); sync.setStatus(status); sync.setLastSyncedAt(OffsetDateTime.now());
        if (setCursor) sync.setCursor(cursor);
        syncRepository.save(sync);
    }

    private static JobProvider.JobBatch unwrap(CompletableFuture<JobProvider.JobBatch> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) throw runtime;
            throw ex;
        }
    }

    private JobPosting upsert(JobPosting candidate) {
        JobPosting existing = postingRepository.findByProviderAndExternalId(candidate.getProvider(), candidate.getExternalId()).orElse(null);
        if (existing == null) existing = postingRepository.findByDedupeHash(candidate.getDedupeHash()).orElse(null);
        if (existing != null) { candidate.setId(existing.getId()); candidate.setCreatedAt(existing.getCreatedAt()); }
        return postingRepository.save(candidate);
    }

    private String key(JobQuery query) {
        String key = String.valueOf(query.keywords()) + "|" + query.roles() + "|" + query.locations();
        return query.postedWithinHours() == null ? key : key + "|" + query.postedWithinHours() + "h";
    }

    private boolean hasRequiredFields(JobProvider.ProviderJob job) {
        return job.externalId() != null && !job.externalId().isBlank()
                && job.company() != null && !job.company().isBlank()
                && job.title() != null && !job.title().isBlank()
                && job.applyUrl() != null && !job.applyUrl().isBlank();
    }
}
