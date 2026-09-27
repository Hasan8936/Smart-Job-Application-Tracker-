package com.smartjobtracker.jobs.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class AshbyClient {
    private static final Logger log = LoggerFactory.getLogger(AshbyClient.class);
    private final ProviderHttpClient http; private final ObjectMapper mapper;
    public AshbyClient(org.springframework.web.client.RestClient.Builder builder, ObjectMapper mapper,
                       com.smartjobtracker.config.JobProviderConfig config) {
        http = new ProviderHttpClient(builder, config.getMinIntervalMs(), config.getMaxRetries()); this.mapper = mapper;
    }
    public List<JobProvider.ProviderJob> search(List<String> boards, JobProvider.JobQuery query) {
        List<JobProvider.ProviderJob> jobs = new ArrayList<>();
        for (String board : boards == null ? List.<String>of() : boards) {
            try {
                for (JsonNode n : http.get("https://api.ashbyhq.com/posting-api/job-board/" + board + "?includeCompensation=true").path("jobs"))
                    jobs.add(parse(n, board));
            } catch (ProviderHttpClient.ProviderUnavailableException e) {
                log.warn("Ashby board '{}' unavailable ({}), skipping", board, e.getMessage());
            }
        }
        return jobs;
    }
    public JobProvider.ProviderJob find(List<String> boards, String id) { return search(boards, new JobProvider.JobQuery(null, List.of(), List.of())).stream().filter(j -> id.equals(j.externalId())).findFirst().orElse(null); }
    /** Salary only when the company chose to publish compensation on its job board. */
    JobProvider.ProviderJob parse(JsonNode n, String board) {
        SalaryInfo pay = SalaryInfo.fromAshby(n.path("compensation"));
        String workplace = n.path("workplaceType").asText(null);
        if ((workplace == null || workplace.isBlank()) && n.path("isRemote").asBoolean(false)) workplace = "Remote";
        return new JobProvider.ProviderJob(board + ":" + n.path("id").asText(), board, n.path("title").asText(null), n.path("location").asText(null),
                n.path("employmentType").asText(null), workplace, n.path("jobUrl").asText(null), n.path("publishedAt").asText(null),
                n.path("descriptionHtml").asText(null), null, pay.min(), pay.max(), pay.currency(), raw(n), pay.period());
    }
    private String raw(JsonNode n) { try { return mapper.writeValueAsString(n); } catch (Exception e) { return "{}"; } }
}