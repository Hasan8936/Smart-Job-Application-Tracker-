package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.jobs.provider.JobProvider;
import com.smartjobtracker.jobs.provider.JobProvider.JobQuery;
import com.smartjobtracker.model.JobProviderSync;
import com.smartjobtracker.repository.JobPostingRepository;
import com.smartjobtracker.repository.JobProviderSyncRepository;
import com.smartjobtracker.repository.JobSkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JobSyncServiceTest {
    private final JobProviderSyncRepository syncs = mock(JobProviderSyncRepository.class);

    /** A source that only returns once the other source has started, so it can only finish if they run in parallel. */
    private static JobProvider source(String id, CountDownLatch started, CountDownLatch waitFor) {
        return new JobProvider() {
            public String id() { return id; }
            public boolean isEnabled() { return true; }
            public Set<Capability> capabilities() { return Set.of(); }
            public ProviderJob fetchJobDetails(String externalId) { return null; }
            public JobBatch search(JobQuery query, String cursor) {
                started.countDown();
                try {
                    if (waitFor != null && !waitFor.await(5, TimeUnit.SECONDS)) throw new IllegalStateException(id + " ran alone");
                } catch (InterruptedException e) { throw new IllegalStateException(e); }
                return new JobBatch(List.of(), null);
            }
        };
    }

    private JobSyncService service(List<JobProvider> providers, int cooldownMinutes) {
        when(syncs.findByProviderAndQueryKey(anyString(), anyString())).thenReturn(Optional.empty());
        return new JobSyncService(providers, mock(JobNormalizer.class), new JobDeduplicator(), mock(JobPostingRepository.class),
                syncs, mock(JobSkillRepository.class), new JobSkillExtractor(), new SyncProgressStore(),
                mock(SalaryEstimator.class), new ScriptFilter(List.of("ARABIC")), mock(PlatformTransactionManager.class), cooldownMinutes);
    }

    @Test
    void sourcesAreFetchedInParallel() {
        CountDownLatch aStarted = new CountDownLatch(1), bStarted = new CountDownLatch(1);
        JobSyncService sync = service(List.of(source("a", aStarted, bStarted), source("b", bStarted, aStarted)), 10);
        JobSyncService.SyncResult result = sync.sync(new JobQuery("java", List.of(), List.of()));
        assertEquals(0, result.saved());
        assertTrue(result.providerErrors().isEmpty(), "both finished: " + result.providerErrors());
    }

    @Test
    void recentlySyncedSourceIsSkippedButFailedOrOldOnesRun() {
        CountDownLatch fresh = new CountDownLatch(1), failed = new CountDownLatch(1);
        JobSyncService sync = service(List.of(source("fresh", fresh, null), source("failed", failed, null)), 10);
        JobProviderSync recent = new JobProviderSync();
        recent.setStatus("SUCCESS"); recent.setLastSyncedAt(OffsetDateTime.now().minusMinutes(2));
        JobProviderSync recentFailure = new JobProviderSync();
        recentFailure.setStatus("FAILED"); recentFailure.setLastSyncedAt(OffsetDateTime.now().minusMinutes(2));
        when(syncs.findByProviderAndQueryKey(eq("fresh"), anyString())).thenReturn(Optional.of(recent));
        when(syncs.findByProviderAndQueryKey(eq("failed"), anyString())).thenReturn(Optional.of(recentFailure));

        JobSyncService.SyncResult result = sync.sync(new JobQuery("java", List.of(), List.of()));
        assertEquals(List.of("fresh"), result.upToDate());
        assertEquals(1, fresh.getCount(), "skipped source was not searched");
        assertEquals(0, failed.getCount(), "a failed sync is retried straight away");
        verify(syncs, atLeastOnce()).save(any());
    }

    @Test
    void cooldownZeroAlwaysSearches() {
        CountDownLatch started = new CountDownLatch(1);
        JobSyncService sync = service(List.of(source("a", started, null)), 0);
        JobProviderSync recent = new JobProviderSync();
        recent.setStatus("SUCCESS"); recent.setLastSyncedAt(OffsetDateTime.now());
        when(syncs.findByProviderAndQueryKey(eq("a"), anyString())).thenReturn(Optional.of(recent));
        assertTrue(sync.sync(new JobQuery("java", List.of(), List.of())).upToDate().isEmpty());
        assertEquals(0, started.getCount());
    }
}
