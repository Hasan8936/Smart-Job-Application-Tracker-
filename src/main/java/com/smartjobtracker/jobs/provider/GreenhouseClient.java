package com.smartjobtracker.jobs.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class GreenhouseClient {
    private static final Logger log = LoggerFactory.getLogger(GreenhouseClient.class);
    private final ProviderHttpClient http;
    private final ObjectMapper mapper;
    public GreenhouseClient(RestClient.Builder builder, ObjectMapper mapper,
                            com.smartjobtracker.config.JobProviderConfig config) {
        http = new ProviderHttpClient(builder, config.getMinIntervalMs(), config.getMaxRetries()); this.mapper = mapper;
    }
    public List<JobProvider.ProviderJob> search(List<String> boards, JobProvider.JobQuery query) {
        List<JobProvider.ProviderJob> jobs = new ArrayList<>();
        for (String board : safe(boards)) {
            try {
                JsonNode root = http.get("https://boards-api.greenhouse.io/v1/boards/" + enc(board) + "/jobs?content=true&pay_transparency=true");
                for (JsonNode node : root.path("jobs")) jobs.add(parse(node, board));
            } catch (ProviderHttpClient.ProviderUnavailableException e) {
                log.warn("Greenhouse board '{}' unavailable ({}), skipping", board, e.getMessage());
            }
        }
        return jobs;
    }
    public JobProvider.ProviderJob find(List<String> boards, String id) {
        for (JobProvider.ProviderJob job : search(boards, new JobProvider.JobQuery(null, List.of(), List.of())))
            if (id.equals(job.externalId())) return job;
        return null;
    }
    JobProvider.ProviderJob parse(JsonNode n, String board) {
        // first_published is when the job went live; updated_at changes on any edit.
        String posted = text(n, "first_published") != null ? text(n, "first_published") : text(n, "updated_at");
        String company = text(n, "company_name") != null ? text(n, "company_name") : board;
        SalaryInfo pay = SalaryInfo.fromGreenhouse(n.path("pay_input_ranges"));
        return new JobProvider.ProviderJob(board + ":" + n.path("id").asText(), company,
                text(n, "title"), n.path("location").path("name").asText(null), null, null,
                text(n, "absolute_url"), posted, text(n, "content"), null,
                pay.min(), pay.max(), pay.currency(), raw(n), pay.period());
    }
    private String text(JsonNode n, String key) { return n.path(key).isMissingNode() ? null : n.path(key).asText(null); }
    private String raw(JsonNode n) { try { return mapper.writeValueAsString(n); } catch (Exception e) { return "{}"; } }
    private List<String> safe(List<String> values) { return values == null ? List.of() : values; }
    private String enc(String value) { return value.replace(" ", "%20"); }
}