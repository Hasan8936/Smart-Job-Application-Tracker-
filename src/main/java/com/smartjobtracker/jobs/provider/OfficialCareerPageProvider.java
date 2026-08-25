package com.smartjobtracker.jobs.provider;

import com.smartjobtracker.config.JobProviderConfig;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

@Component
public class OfficialCareerPageProvider implements JobProvider {
    private final JobProviderConfig config;
    private final GreenhouseClient greenhouse;
    private final LeverClient lever;
    private final AshbyClient ashby;

    public OfficialCareerPageProvider(JobProviderConfig config, GreenhouseClient greenhouse,
                                     LeverClient lever, AshbyClient ashby) {
        this.config = config;
        this.greenhouse = greenhouse;
        this.lever = lever;
        this.ashby = ashby;
    }

    @Override public String id() { return "official"; }
    @Override public boolean isEnabled() {
        return config.getGreenhouse().isEnabled() || config.getLever().isEnabled() || config.getAshby().isEnabled();
    }
    @Override public Set<Capability> capabilities() {
        return EnumSet.of(Capability.POSTED_DATE, Capability.OFFICIAL_APPLY_URL);
    }

    @Override public JobBatch search(JobQuery query, String cursor) {
        List<ProviderJob> jobs = new ArrayList<>();
        if (config.getGreenhouse().isEnabled()) jobs.addAll(greenhouse.search(config.getGreenhouse().getBoards(), query));
        if (config.getLever().isEnabled()) jobs.addAll(lever.search(config.getLever().getSites(), query));
        if (config.getAshby().isEnabled()) jobs.addAll(ashby.search(config.getAshby().getBoards(), query));
        List<ProviderJob> results = jobs.stream().filter(job -> matches(job, query)).toList();
        return new JobBatch(results, null);
    }

    @Override public ProviderJob fetchJobDetails(String externalId) {
        if (config.getGreenhouse().isEnabled()) {
            ProviderJob job = greenhouse.find(config.getGreenhouse().getBoards(), externalId);
            if (job != null) return job;
        }
        if (config.getLever().isEnabled()) {
            ProviderJob job = lever.find(config.getLever().getSites(), externalId);
            if (job != null) return job;
        }
        if (config.getAshby().isEnabled()) {
            ProviderJob job = ashby.find(config.getAshby().getBoards(), externalId);
            if (job != null) return job;
        }
        throw new IllegalArgumentException("Job not found");
    }

    private boolean matches(ProviderJob job, JobQuery query) {
        if (query.keywords() == null || query.keywords().isBlank()) return true;
        String text = ((job.title() == null ? "" : job.title()) + " " +
                (job.description() == null ? "" : job.description())).toLowerCase();
        return text.contains(query.keywords().toLowerCase());
    }
}