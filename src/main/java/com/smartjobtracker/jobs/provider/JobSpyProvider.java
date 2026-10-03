package com.smartjobtracker.jobs.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartjobtracker.config.JobProviderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

@Component
public class JobSpyProvider implements JobProvider {
    private static final Logger log = LoggerFactory.getLogger(JobSpyProvider.class);
    static final int MAX_SEARCHES = 3;

    private final JobProviderConfig.JobSpySettings config;
    private final ProviderHttpClient http;
    private final ObjectMapper mapper;

    public JobSpyProvider(JobProviderConfig config, @Qualifier("jobSpyRestClientBuilder") RestClient.Builder builder, ObjectMapper mapper) {
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
            String location = (query.locations() == null || query.locations().isEmpty() || query.locations().get(0).isBlank())
                    ? (config.getDefaultLocation() == null ? "" : config.getDefaultLocation()) : query.locations().get(0);
            // Typed keywords are one search. Otherwise each role is its own search (run in parallel by the service):
            // gluing twelve roles into one query string returned few, poorly matched results.
            List<String> searches = (query.keywords() != null && !query.keywords().isBlank())
                    ? List.of(query.keywords().trim())
                    : query.roles().stream().filter(r -> r != null && !r.isBlank()).map(String::trim).distinct().limit(MAX_SEARCHES).toList();
            String keywords = searches.isEmpty() ? "" : searches.get(0);

            ObjectNode body = mapper.createObjectNode();
            body.put("keywords", keywords); // older service versions only read this
            body.set("queries", mapper.valueToTree(searches));
            body.put("location", location);
            body.put("results_wanted", config.getResultsWanted());
            body.put("hours_old", query.postedWithinHours() != null ? query.postedWithinHours() : config.getHoursOld());
            body.set("site_names", mapper.valueToTree(config.getSiteNames()));
            body.put("linkedin_fetch_description", config.isLinkedinFetchDescription());
            if (config.getCountry() != null && !config.getCountry().isBlank()) body.put("country_indeed", config.getCountry());

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
                    j.toString(),
                    period(text(j, "salaryPeriod"))
                ));
            }
            log.info("JobSpy returned {} jobs for {} search(es)", jobs.size(), searches.size());
            return new JobBatch(jobs, null);
        } catch (Exception e) {
            log.error("JobSpy search failed: {}", e.getMessage(), e);
            throw new RuntimeException("JobSpy search failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ProviderJob fetchJobDetails(String externalId) { return null; }

    /** JobSpy intervals: yearly, monthly, weekly, daily, hourly. */
    private String period(String interval) {
        return switch (interval.toLowerCase(java.util.Locale.ROOT)) {
            case "yearly" -> "YEAR";
            case "monthly" -> "MONTH";
            case "weekly" -> "WEEK";
            case "daily" -> "DAY";
            case "hourly" -> "HOUR";
            default -> null;
        };
    }

    private String text(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return (v == null || v.isNull()) ? "" : v.asText("");
    }

    private Integer num(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return (v == null || v.isNull() || !v.isNumber()) ? null : v.intValue();
    }
}
