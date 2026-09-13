package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.jobs.provider.JobProvider.JobQuery;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SyncRunner {

    private final JobSyncService syncService;
    private final SyncProgressStore progressStore;

    public SyncRunner(JobSyncService syncService, SyncProgressStore progressStore) {
        this.syncService = syncService;
        this.progressStore = progressStore;
    }

    @Async
    public void runAsync(String syncId, JobQuery query) {
        try {
            JobSyncService.SyncResult result = syncService.sync(syncId, query);
            progressStore.complete(syncId, result.saved(), result.providerErrors());
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            progressStore.complete(syncId, 0, Map.of("error", msg));
        }
    }
}
