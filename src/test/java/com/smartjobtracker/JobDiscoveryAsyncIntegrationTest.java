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
 * Integration tests for the async job discovery flow:
 * POST /api/jobs/discover → returns {syncId} + 202
 * GET  /api/jobs/discover/progress/{syncId} → returns SyncProgressDto
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JobDiscoveryAsyncIntegrationTest {

    @LocalServerPort
    private int port;

    private String base() { return "http://localhost:" + port; }

    /** Register a unique test user and return their JWT token. */
    private String jwt(RestTemplate rest, String suffix) {
        String auth = base() + "/api/auth";
        RegisterRequest r = new RegisterRequest();
        r.setName("Disc" + suffix);
        r.setEmail("disc" + suffix + "@example.com");
        r.setPassword("pass123");
        rest.postForEntity(auth + "/register", r, String.class);

        AuthRequest a = new AuthRequest();
        a.setEmail("disc" + suffix + "@example.com");
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
    void discoverReturns202WithSyncId() {
        RestTemplate rest = new RestTemplate();
        String token = jwt(rest, "sync1");
        HttpEntity<String> req = new HttpEntity<>("{}", headers(token));

        // No providers enabled in test profile → service throws IllegalStateException.
        // The async wrapper catches it and marks progress as error/done.
        // Either way, the HTTP response must be 202 Accepted with a syncId immediately.
        ResponseEntity<String> res = rest.exchange(
                base() + "/api/jobs/discover", HttpMethod.POST, req, String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(res.getBody()).contains("syncId");
        // syncId should be a UUID — 36 chars with hyphens
        String body = res.getBody();
        String syncId = body.replaceAll(".*\"syncId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        assertThat(syncId).matches("[0-9a-f-]{36}");
    }

    @Test
    void progressEndpointReturnsProgressForValidSyncId() throws InterruptedException {
        RestTemplate rest = new RestTemplate();
        String token = jwt(rest, "sync2");
        HttpEntity<String> req = new HttpEntity<>("{}", headers(token));

        // Start sync
        ResponseEntity<String> startRes = rest.exchange(
                base() + "/api/jobs/discover", HttpMethod.POST, req, String.class);
        assertThat(startRes.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String syncId = startRes.getBody().replaceAll(".*\"syncId\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        // Poll progress a few times until done=true (max 30s)
        HttpEntity<Void> pollReq = new HttpEntity<>(headers(token));
        String progressUrl = base() + "/api/jobs/discover/progress/" + syncId;
        boolean done = false;
        for (int attempt = 0; attempt < 20; attempt++) {
            Thread.sleep(1500);
            ResponseEntity<String> prog = rest.exchange(progressUrl, HttpMethod.GET, pollReq, String.class);
            assertThat(prog.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(prog.getBody()).contains("done");
            assertThat(prog.getBody()).contains("totalSaved");
            if (prog.getBody().contains("\"done\":true")) { done = true; break; }
        }
        assertThat(done).as("Sync should complete within 30s").isTrue();
    }

    @Test
    void progressEndpointReturns404ForUnknownSyncId() {
        RestTemplate rest = new RestTemplate();
        String token = jwt(rest, "sync3");
        HttpEntity<Void> req = new HttpEntity<>(headers(token));

        assertThatThrownBy(() -> rest.exchange(
                base() + "/api/jobs/discover/progress/00000000-dead-beef-0000-000000000000",
                HttpMethod.GET, req, String.class))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void discoverRequiresAuthentication() {
        RestTemplate rest = new RestTemplate();
        HttpEntity<String> req = new HttpEntity<>("{}", new HttpHeaders());
        assertThatThrownBy(() -> rest.exchange(
                base() + "/api/jobs/discover", HttpMethod.POST, req, String.class))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void jobsListEndpointRequiresAuthentication() {
        RestTemplate rest = new RestTemplate();
        assertThatThrownBy(() -> rest.getForEntity(base() + "/api/jobs", String.class))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void jobsListReturnsPagedResultsWhenAuthenticated() {
        RestTemplate rest = new RestTemplate();
        String token = jwt(rest, "sync4");
        HttpEntity<Void> req = new HttpEntity<>(headers(token));
        ResponseEntity<String> res = rest.exchange(
                base() + "/api/jobs?size=5", HttpMethod.GET, req, String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("content");
        assertThat(res.getBody()).contains("totalElements");
    }
}
