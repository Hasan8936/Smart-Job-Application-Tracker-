package com.smartjobtracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.repository.JobPostingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** "Mark applied" on a discovered job must create exactly one application the user sees under Applications. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class MarkAppliedIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JobPostingRepository jobs;

    private final RestTemplate rest = new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory());
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void markAppliedCreatesOneApplicationEvenWithALongDescription() throws Exception {
        JobPosting posting = new JobPosting();
        posting.setProvider("greenhouse"); posting.setExternalId("mark-applied-it-1"); posting.setDedupeHash("mark-applied-it-1");
        posting.setCompany("Stripe"); posting.setTitle("Backend Engineer"); posting.setApplyUrl("https://example.test/apply");
        posting.setDescription("Build payments infrastructure. ".repeat(200)); // ~6k chars, like real postings
        long jobId = jobs.save(posting).getId();

        String base = "http://localhost:" + port + "/api";
        rest.postForEntity(base + "/auth/register", Map.of("name", "A", "email", "mark-applied-it@example.com", "password", "pass1234"), String.class);
        String login = rest.postForEntity(base + "/auth/login", Map.of("email", "mark-applied-it@example.com", "password", "pass1234"), String.class).getBody();
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(mapper.readTree(login).get("token").asText());
        HttpEntity<Void> auth = new HttpEntity<>(h);

        ResponseEntity<String> first = rest.exchange(base + "/jobs/" + jobId + "/applied", HttpMethod.POST, auth, String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(first.getBody()).get("applicationId").isNumber()).isTrue();
        // Idempotent: clicking again must not create a duplicate application.
        rest.exchange(base + "/jobs/" + jobId + "/applied", HttpMethod.POST, auth, String.class);

        JsonNode apps = mapper.readTree(rest.exchange(base + "/applications", HttpMethod.GET, auth, String.class).getBody());
        assertThat(apps.size()).isEqualTo(1);
        assertThat(apps.get(0).get("companyName").asText()).isEqualTo("Stripe");
        assertThat(apps.get(0).get("roleTitle").asText()).isEqualTo("Backend Engineer");
        assertThat(apps.get(0).get("status").asText()).isEqualTo("APPLIED");
    }
}
