package com.smartjobtracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Onboarding job preferences: never asked → skip → save (validated) → skip can't erase it → recommended endpoint. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class JobPreferencesIntegrationTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory());
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void preferencesLifecycle() throws Exception {
        String base = "http://localhost:" + port + "/api";
        rest.postForEntity(base + "/auth/register", Map.of("name", "P", "email", "prefs-it@example.com", "password", "pass1234"), String.class);
        String login = rest.postForEntity(base + "/auth/login", Map.of("email", "prefs-it@example.com", "password", "pass1234"), String.class).getBody();
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(mapper.readTree(login).get("token").asText());
        h.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> never = rest.exchange(base + "/job-preferences", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(never.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        rest.exchange(base + "/job-preferences/skip", HttpMethod.POST, new HttpEntity<>(h), String.class);
        JsonNode skipped = mapper.readTree(rest.exchange(base + "/job-preferences", HttpMethod.GET, new HttpEntity<>(h), String.class).getBody());
        assertThat(skipped.get("status").asText()).isEqualTo("SKIPPED");

        Map<String, Object> invalid = Map.of("roles", List.of(), "workModes", List.of("SOMETIMES"));
        assertThatThrownBy(() -> rest.exchange(base + "/job-preferences", HttpMethod.PUT, new HttpEntity<>(invalid, h), String.class))
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .hasMessageContaining("validation failed");

        Map<String, Object> valid = Map.of("roles", List.of(" Data Analyst ", "data analyst", "Customer Care"),
                "experienceLevel", "FRESHER", "locations", List.of("Bangalore"), "workModes", List.of("REMOTE", "HYBRID"),
                "jobTypes", List.of("FULL_TIME"), "minSalaryLpa", 4);
        JsonNode saved = mapper.readTree(rest.exchange(base + "/job-preferences", HttpMethod.PUT, new HttpEntity<>(valid, h), String.class).getBody());
        assertThat(saved.get("status").asText()).isEqualTo("SAVED");
        assertThat(saved.get("roles").toString()).isEqualTo("[\"Data Analyst\",\"Customer Care\"]");
        assertThat(saved.get("minSalaryLpa").asInt()).isEqualTo(4);

        rest.exchange(base + "/job-preferences/skip", HttpMethod.POST, new HttpEntity<>(h), String.class);
        JsonNode stillSaved = mapper.readTree(rest.exchange(base + "/job-preferences", HttpMethod.GET, new HttpEntity<>(h), String.class).getBody());
        assertThat(stillSaved.get("status").asText()).isEqualTo("SAVED");

        ResponseEntity<String> recommended = rest.exchange(base + "/jobs/recommended?size=5", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(recommended.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(recommended.getBody()).has("content")).isTrue();
    }

    @Test
    public void requiresAuthentication() {
        assertThatThrownBy(() -> rest.getForEntity("http://localhost:" + port + "/api/job-preferences", String.class))
                .isInstanceOf(HttpClientErrorException.class);
    }
}
