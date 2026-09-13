package com.smartjobtracker.jobs.discovery;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SyncProgressStore {

    private final ConcurrentHashMap<String, SyncProgress> store = new ConcurrentHashMap<>();

    public void init(String syncId) {
        store.put(syncId, new SyncProgress("running", null, 0, 0, false, Map.of()));
    }

    public void update(String syncId, String provider, int providerJobs, int totalSaved) {
        store.computeIfPresent(syncId, (k, p) ->
                new SyncProgress("running", provider, providerJobs, totalSaved, false, p.errors()));
    }

    public void complete(String syncId, int totalSaved, Map<String, String> errors) {
        store.computeIfPresent(syncId, (k, p) ->
                new SyncProgress("done", null, 0, totalSaved, true, errors));
    }

    public SyncProgress get(String syncId) {
        return store.get(syncId);
    }

    public record SyncProgress(
            String status,
            String currentProvider,
            int providerJobs,
            int totalSaved,
            boolean done,
            Map<String, String> errors
    ) {}
}
