package com.smartjobtracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A new user's missing profile / universal resume: 404 by default (unchanged), 204 with optional=true. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class OptionalResourceIntegrationTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory());

    @Test
    public void missingProfileAndUniversalResumeAre404ByDefaultAnd204WhenOptional() throws Exception {
        String base = "http://localhost:" + port + "/api";
        rest.postForEntity(base + "/auth/register", Map.of("name", "N", "email", "optional-it@example.com", "password", "pass1234"), String.class);
        String body = rest.postForEntity(base + "/auth/login", Map.of("email", "optional-it@example.com", "password", "pass1234"), String.class).getBody();
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(new ObjectMapper().readTree(body).get("token").asText());
        HttpEntity<Void> auth = new HttpEntity<>(h);

        for (String path : new String[]{"/profile", "/resume/universal"}) {
            assertThatThrownBy(() -> rest.exchange(base + path, HttpMethod.GET, auth, String.class))
                    .isInstanceOf(HttpClientErrorException.NotFound.class);
            ResponseEntity<String> optional = rest.exchange(base + path + "?optional=true", HttpMethod.GET, auth, String.class);
            assertThat(optional.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }
}
