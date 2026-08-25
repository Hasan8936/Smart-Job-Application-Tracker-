package com.smartjobtracker.jobs.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class LeverClient {
    private final ProviderHttpClient http; private final ObjectMapper mapper;
    public LeverClient(org.springframework.web.client.RestClient.Builder builder, ObjectMapper mapper,
                       com.smartjobtracker.config.JobProviderConfig config) {
        http = new ProviderHttpClient(builder, config.getMinIntervalMs(), config.getMaxRetries()); this.mapper = mapper;
    }
    public List<JobProvider.ProviderJob> search(List<String> sites, JobProvider.JobQuery query) {
        List<JobProvider.ProviderJob> jobs = new ArrayList<>();
        for (String site : sites == null ? List.<String>of() : sites) for (JsonNode n : http.get("https://api.lever.co/v0/postings/" + site + "?mode=json"))
            jobs.add(new JobProvider.ProviderJob(site + ":" + n.path("id").asText(), site, n.path("text").asText(null),
                    n.path("categories").path("location").asText(null), n.path("categories").path("commitment").asText(null), null,
                    n.path("hostedUrl").asText(null), n.path("createdAt").asText(null), n.path("descriptionPlain").asText(null), null, null, null, null, raw(n)));
        return jobs;
    }
    public JobProvider.ProviderJob find(List<String> sites, String id) { return search(sites, new JobProvider.JobQuery(null, List.of(), List.of())).stream().filter(j -> id.equals(j.externalId())).findFirst().orElse(null); }
    private String raw(JsonNode n) { try { return mapper.writeValueAsString(n); } catch (Exception e) { return "{}"; } }
}