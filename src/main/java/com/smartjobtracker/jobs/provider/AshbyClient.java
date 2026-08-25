package com.smartjobtracker.jobs.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class AshbyClient {
    private final ProviderHttpClient http; private final ObjectMapper mapper;
    public AshbyClient(org.springframework.web.client.RestClient.Builder builder, ObjectMapper mapper,
                       com.smartjobtracker.config.JobProviderConfig config) {
        http = new ProviderHttpClient(builder, config.getMinIntervalMs(), config.getMaxRetries()); this.mapper = mapper;
    }
    public List<JobProvider.ProviderJob> search(List<String> boards, JobProvider.JobQuery query) {
        List<JobProvider.ProviderJob> jobs = new ArrayList<>();
        for (String board : boards == null ? List.<String>of() : boards) for (JsonNode n : http.get("https://api.ashbyhq.com/posting-api/job-board/" + board).path("jobs"))
            jobs.add(new JobProvider.ProviderJob(board + ":" + n.path("id").asText(), board, n.path("title").asText(null), n.path("location").asText(null),
                    n.path("employmentType").asText(null), null, n.path("jobUrl").asText(null), n.path("publishedAt").asText(null),
                    n.path("descriptionHtml").asText(null), null, null, null, null, raw(n)));
        return jobs;
    }
    public JobProvider.ProviderJob find(List<String> boards, String id) { return search(boards, new JobProvider.JobQuery(null, List.of(), List.of())).stream().filter(j -> id.equals(j.externalId())).findFirst().orElse(null); }
    private String raw(JsonNode n) { try { return mapper.writeValueAsString(n); } catch (Exception e) { return "{}"; } }
}