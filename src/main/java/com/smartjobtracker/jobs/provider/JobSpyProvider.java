package com.smartjobtracker.jobs.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartjobtracker.config.JobProviderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

@Component
public class JobSpyProvider implements JobProvider {
    private static final Logger log = LoggerFactory.getLogger(JobSpyProvider.class);

    private final JobProviderConfig.JobSpySettings config;
    private final ProviderHttpClient http;
    private final ObjectMapper mapper;

    public JobSpyProvider(JobProviderConfig config, RestClient.Builder builder, ObjectMapper mapper) {
        this.config = config.getJobspy();
        this.mapper = mapper;
        this.http = new ProviderHttpClient(builder, config.getMinIntervalMs(), config.getMaxRetries());
    }

    @Override
    public String id() { return "jobspy"; }

    @Override
    public boolean isEnabled() {
        return config.isEnabled() && config.getServiceUrl() != null && !config.getServiceUrl().isBlank();
    }

    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(Capability.SALARY, Capability.POSTED_DATE, Capability.OFFICIAL_APPLY_URL);
    }

    @Override
    public JobBatch search(JobQuery query, String cursor) {
        if (!isEnabled()) return new JobBatch(List.of(), null);
        try {
            String location = (query.locations() == null || query.locations().isEmpty()) ? "" : query.locations().get(0);
            String keywords = ((query.keywords() == null ? "" : query.keywords()) + " " +
                               String.join(" ", query.roles() == null ? List.of() : query.roles())).trim();

            ObjectNode body = mapper.createObjectNode();
            body.put("keywords", keywords);
            body.put("location", location);
            body.put("results_wanted", config.getResultsWanted());
            body.put("hours_old", config.getHoursOld());
            body.set("site_names", mapper.valueToTree(config.getSiteNames()));

            JsonNode root = http.post(config.getServiceUrl() + "/search", body);

            List<ProviderJob> jobs = new ArrayList<>();
            for (JsonNode j : root.path("jobs")) {
                String externalId = text(j, "externalId");
                String applyUrl   = text(j, "applyUrl");
                String company    = text(j, "company");
                String title      = text(j, "title");
                if ((externalId.isBlank() && applyUrl.isBlank()) || company.isBlank() || title.isBlank()) continue;
                jobs.add(new ProviderJob(
                    externalId.isBlank() ? applyUrl : externalId,
                    company,
                    title,
                    text(j, "location"),
                    text(j, "employmentType"),
                    text(j, "workMode"),
                    applyUrl,
                    text(j, "postedAt"),
                    text(j, "description"),
                    null,
                    num(j, "salaryMin"),
                    num(j, "salaryMax"),
                    text(j, "salaryCurrency"),
                    j.toString()
                ));
            }
            log.info("JobSpy returned {} jobs for keywords='{}'", jobs.size(), keywords);
            return new JobBatch(jobs, null);
        } catch (Exception e) {
            log.error("JobSpy search failed: {}", e.getMessage(), e);
            throw new RuntimeException("JobSpy search failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ProviderJob fetchJobDetails(String externalId) { return null; }

    private String text(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return (v == null || v.isNull()) ? "" : v.asText("");
    }

    private Integer num(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return (v == null || v.isNull() || !v.isNumber()) ? null : v.intValue();
    }
}
