package com.smartjobtracker.jobs.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.config.JobProviderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
public class ApifyJobProvider implements JobProvider {
    private static final Logger log = LoggerFactory.getLogger(ApifyJobProvider.class);
    private final JobProviderConfig.ApifySettings config;
    private final ProviderHttpClient http;
    private final ObjectMapper mapper;
    public ApifyJobProvider(JobProviderConfig config, org.springframework.web.client.RestClient.Builder builder,
                            ObjectMapper mapper) {
        this.config = config.getApify(); this.mapper = mapper;
        http = new ProviderHttpClient(builder, config.getMinIntervalMs(), config.getMaxRetries());
    }
    @Override public String id() { return "apify"; }
    @Override public boolean isEnabled() { return config.isEnabled() && hasCredentials(); }
    @Override public Set<Capability> capabilities() { return EnumSet.of(Capability.OFFICIAL_APPLY_URL); }
    @Override public JobBatch search(JobQuery query, String cursor) {
        if (!isEnabled()) throw new ProviderHttpClient.ProviderUnavailableException("Apify provider is not configured");
        java.util.List<ProviderJob> jobs = new java.util.ArrayList<>();
        ProviderHttpClient.ProviderUnavailableException lastFailure = null;
        for (String actor : config.getActors()) {
            try {
                jobs.addAll(runActor(actor, query));
            } catch (RuntimeException ex) {
                log.warn("Apify actor failed, continuing with remaining actors actor={}", actor, ex);
                lastFailure = ex instanceof ProviderHttpClient.ProviderUnavailableException pue ? pue
                        : new ProviderHttpClient.ProviderUnavailableException("Apify actor " + actor + " failed", ex);
            }
        }
        if (jobs.isEmpty() && lastFailure != null) throw lastFailure;
        return new JobBatch(jobs, null);
    }
    private List<ProviderJob> runActor(String actor, JobQuery query) {
        JsonNode input = mapper.createObjectNode().put("query", query.keywords() == null ? "" : query.keywords());
        JsonNode run = http.post("https://api.apify.com/v2/acts/" + actorId(actor)
                + "/runs?token=" + config.getToken() + "&waitForFinish=60", input);
        String datasetId = run.path("data").path("defaultDatasetId").asText(null);
        if (datasetId == null || datasetId.isBlank()) throw new ProviderHttpClient.ProviderUnavailableException("Apify actor " + actor + " returned no dataset");
        JsonNode items = http.get("https://api.apify.com/v2/datasets/" + datasetId + "/items?clean=true");
        java.util.List<ProviderJob> jobs = new java.util.ArrayList<>();
        for (JsonNode item : items) jobs.add(parse(item));
        return jobs;
    }
    /**
     * Apify's REST API identifies an actor as either its raw ID or a tilde-separated
     * "owner~actor-name" pair. The Apify Store and console display actor slugs with a
     * slash ("owner/actor-name") instead, which is what people naturally copy — so accept
     * either form here and normalize to the one the API actually expects.
     */
    private String actorId(String actor) { return actor.replaceFirst("/", "~"); }
    @Override public ProviderJob fetchJobDetails(String externalId) {
        return search(new JobQuery(null, java.util.List.of(), java.util.List.of()), null).jobs().stream()
                .filter(job -> externalId.equals(job.externalId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
    }
    private boolean hasCredentials() { return config.getToken() != null && !config.getToken().isBlank()
            && config.getActors() != null && !config.getActors().isEmpty(); }
    private ProviderJob parse(JsonNode item) {
        String id = first(item, "externalId", "id", "jobId");
        String applyUrl = first(item, "applyUrl", "url", "jobUrl");
        return new ProviderJob(id, first(item, "company", "companyName"), first(item, "title", "jobTitle"),
                first(item, "location"), first(item, "employmentType"), first(item, "workMode"), applyUrl,
                first(item, "postedAt", "datePosted"), first(item, "description", "descriptionText"),
                first(item, "logoUrl", "companyLogo"), integer(item, "salaryMin"), integer(item, "salaryMax"),
                first(item, "salaryCurrency", "currency"), item.toString());
    }
    private String first(JsonNode item, String... names) { for (String name : names) if (item.hasNonNull(name)) return item.get(name).asText(); return null; }
    private Integer integer(JsonNode item, String name) { return item.hasNonNull(name) && item.get(name).canConvertToInt() ? item.get(name).asInt() : null; }
}