package com.smartjobtracker;

import com.smartjobtracker.dto.AuthRequest;
import com.smartjobtracker.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the interview prep API.
 * In the test profile Gemini key is not set, so rule-based fallback is used.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InterviewPrepIntegrationTest {

    @LocalServerPort
    private int port;

    private String base() { return "http://localhost:" + port; }

    private String jwt(RestTemplate rest, String suffix) {
        String auth = base() + "/api/auth";
        RegisterRequest r = new RegisterRequest();
        r.setName("IP" + suffix);
        r.setEmail("ip" + suffix + "@example.com");
        r.setPassword("pass123");
        rest.postForEntity(auth + "/register", r, String.class);

        AuthRequest a = new AuthRequest();
        a.setEmail("ip" + suffix + "@example.com");
        a.setPassword("pass123");
        ResponseEntity<String> login = rest.postForEntity(auth + "/login", a, String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return login.getBody().replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return h;
    }

    @Test
    void sessionListIsEmptyForNewUser() {
        RestTemplate rest = new RestTemplate();
        String token = jwt(rest, "ip1");
        HttpEntity<Void> req = new HttpEntity<>(headers(token));
        ResponseEntity<String> res = rest.exchange(
                base() + "/api/interview-prep/sessions", HttpMethod.GET, req, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("[]");
    }

    @Test
    void sessionListRequiresAuthentication() {
        RestTemplate rest = new RestTemplate();
        assertThatThrownBy(() -> rest.getForEntity(
                base() + "/api/interview-prep/sessions", String.class))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void generateRequiresValidBody() {
        RestTemplate rest = new RestTemplate();
        String token = jwt(rest, "ip2");
        // Missing required fields → 400
        HttpEntity<String> req = new HttpEntity<>("{}", headers(token));
        assertThatThrownBy(() -> rest.postForEntity(
                base() + "/api/interview-prep/generate", req, String.class))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    void generateWithTextSourceAndNoResumeReturns4xx() {
        RestTemplate rest = new RestTemplate();
        String token = jwt(rest, "ip3");
        // resumeId 999999 does not exist → service throws IllegalArgumentException → 400 or 404
        String body = "{\"resumeId\":999999,\"source\":\"TEXT\","
                + "\"jobDescriptionText\":\"We need a Java developer with Spring Boot experience.\","
                + "\"questionCount\":10}";
        HttpEntity<String> req = new HttpEntity<>(body, headers(token));
        assertThatThrownBy(() -> rest.postForEntity(
                base() + "/api/interview-prep/generate", req, String.class))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void unknownSessionReturns4xx() {
        RestTemplate rest = new RestTemplate();
        String token = jwt(rest, "ip4");
        HttpEntity<Void> req = new HttpEntity<>(headers(token));
        assertThatThrownBy(() -> rest.exchange(
                base() + "/api/interview-prep/sessions/99999", HttpMethod.GET, req, String.class))
                .isInstanceOf(HttpClientErrorException.class);
    }
}
