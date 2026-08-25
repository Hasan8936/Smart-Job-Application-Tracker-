package com.smartjobtracker.jobs.provider;

import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class FutureJobProvider implements JobProvider {
    @Override public String id() { return "future"; }
    @Override public boolean isEnabled() { return false; }
    @Override public Set<Capability> capabilities() { return Set.of(); }
    @Override public JobBatch search(JobQuery query, String cursor) { return new JobBatch(java.util.List.of(), null); }
    @Override public ProviderJob fetchJobDetails(String externalId) { throw new IllegalArgumentException("Job not found"); }
}