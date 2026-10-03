package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.jobs.provider.JobProvider.JobQuery;
import com.smartjobtracker.model.JobDiscoveryTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SyncRunner {
    private static final Logger log = LoggerFactory.getLogger(SyncRunner.class);

    private final JobSyncService syncService;
    private final JobDiscoveryQueueService queue;
    private final SyncProgressStore progressStore;
    private final boolean workerEnabled;

    public SyncRunner(JobSyncService syncService, JobDiscoveryQueueService queue,
                      SyncProgressStore progressStore,
                      @Value("${app.job-discovery.queue.worker-enabled:true}") boolean workerEnabled) {
        this.syncService = syncService;
        this.queue = queue;
        this.progressStore = progressStore;
        this.workerEnabled = workerEnabled;
    }

    /** Enqueues and returns immediately; the queue row is durable across restarts. */
    public void runAsync(String syncId, JobQuery query) {
        queue.enqueue(syncId, query);
    }

    /** One bounded worker avoids a 0.1-CPU Render instance spawning a thread per request. */
    @Scheduled(fixedDelayString = "${app.job-discovery.queue.poll-ms:1500}",
            initialDelayString = "${app.job-discovery.queue.initial-delay-ms:5000}")
    public void drainQueue() {
        if (!workerEnabled) return;
        JobDiscoveryTask task = queue.claimNext().orElse(null);
        if (task == null) return;
        try {
            JobSyncService.SyncResult result = syncService.sync(task.getId(), queue.query(task));
            queue.complete(task, result.saved());
            progressStore.complete(task.getId(), result.saved(), result.providerErrors(), result.upToDate());
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            boolean retrying = queue.retryOrFail(task, msg);
            if (retrying) progressStore.retry(task.getId(), msg);
            else progressStore.complete(task.getId(), task.getTotalSaved(), Map.of("error", msg));
            log.warn("Discovery queue task {} {}: {}", task.getId(), retrying ? "scheduled for retry" : "failed", msg);
        }
    }
}
