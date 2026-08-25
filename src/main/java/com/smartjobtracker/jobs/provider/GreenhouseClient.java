package com.smartjobtracker.jobs.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class GreenhouseClient {
    private final ProviderHttpClient http;
    private final ObjectMapper mapper;
    public GreenhouseClient(RestClient.Builder builder, ObjectMapper mapper,
                            com.smartjobtracker.config.JobProviderConfig config) {
        http = new ProviderHttpClient(builder, config.getMinIntervalMs(), config.getMaxRetries()); this.mapper = mapper;
    }
    public List<JobProvider.ProviderJob> search(List<String> boards, JobProvider.JobQuery query) {
        List<JobProvider.ProviderJob> jobs = new ArrayList<>();
        for (String board : safe(boards)) {
            JsonNode root = http.get("https://boards-api.greenhouse.io/v1/boards/" + enc(board) + "/jobs?content=true");
            for (JsonNode node : root.path("jobs")) jobs.add(parse(node, board));
        }
        return jobs;
    }
    public JobProvider.ProviderJob find(List<String> boards, String id) {
        for (JobProvider.ProviderJob job : search(boards, new JobProvider.JobQuery(null, List.of(), List.of())))
            if (id.equals(job.externalId())) return job;
        return null;
    }
    private JobProvider.ProviderJob parse(JsonNode n, String board) {
        return new JobProvider.ProviderJob(board + ":" + n.path("id").asText(), board,
                text(n, "title"), n.path("location").path("name").asText(null), null, null,
                text(n, "absolute_url"), text(n, "updated_at"), text(n, "content"), null, null, null, null, raw(n));
    }
    private String text(JsonNode n, String key) { return n.path(key).isMissingNode() ? null : n.path(key).asText(null); }
    private String raw(JsonNode n) { try { return mapper.writeValueAsString(n); } catch (Exception e) { return "{}"; } }
    private List<String> safe(List<String> values) { return values == null ? List.of() : values; }
    private String enc(String value) { return value.replace(" ", "%20"); }
}