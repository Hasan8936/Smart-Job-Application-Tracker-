package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiGatewayTest {

    private static final String ENDPOINT = "https://gemini.test/v1beta/models";
    private static final String OK = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}";
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Long> sleeps = new ArrayList<>();

    private record Setup(GeminiGateway gateway, MockRestServiceServer server) {}

    private Setup setup(int rpm, int maxConcurrent, Duration queueWait) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiGateway gateway = new GeminiGateway(builder.build(), mapper, rpm, maxConcurrent, queueWait, 2, 0,
                Duration.ofHours(1), 100, sleeps::add);
        return new Setup(gateway, server);
    }

    private ObjectNode body(String text) {
        ObjectNode b = mapper.createObjectNode();
        b.putArray("contents").addObject().putArray("parts").addObject().put("text", text);
        return b;
    }

    @Test
    void sendsTheKeyInAHeaderNeverTheUrlAndTurnsThinkingOff() {
        Setup s = setup(60, 4, Duration.ofSeconds(1));
        s.server().expect(requestTo(ENDPOINT + "/gemini-3.6-flash:generateContent"))
                .andExpect(header("x-goog-api-key", "secret-key"))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingBudget").value(0))
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        JsonNode r = s.gateway().generate(ENDPOINT, "gemini-3.6-flash", "secret-key", body("hi"), false);
        assertEquals("ok", r.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText());
        s.server().verify();
    }

    @Test
    void retriesRateLimitsAndOutagesThenSucceeds() {
        Setup s = setup(60, 4, Duration.ofSeconds(1));
        s.server().expect(requestTo(ENDPOINT + "/m:generateContent")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        s.server().expect(requestTo(ENDPOINT + "/m:generateContent")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        s.server().expect(requestTo(ENDPOINT + "/m:generateContent")).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        assertNotNull(s.gateway().generate(ENDPOINT, "m", "k", body("hi"), false));
        assertEquals(2, sleeps.size(), "backed off before each retry");
        assertTrue(sleeps.get(1) > sleeps.get(0), "exponential backoff");
        s.server().verify();
    }

    @Test
    void givesUpAfterMaxRetriesAndDoesNotRetryClientErrors() {
        Setup s = setup(60, 4, Duration.ofSeconds(1));
        s.server().expect(requestTo(ENDPOINT + "/m:generateContent")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThrows(HttpClientErrorException.Forbidden.class, () -> s.gateway().generate(ENDPOINT, "m", "bad", body("hi"), false));
        assertTrue(sleeps.isEmpty());
        s.server().verify();
    }

    @Test
    void modelsThatRejectThinkingConfigAreRetriedWithoutItAndRemembered() {
        Setup s = setup(60, 4, Duration.ofSeconds(1));
        s.server().expect(requestTo(ENDPOINT + "/lite:generateContent"))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig").exists())
                .andRespond(withBadRequest().body("{\"error\":{\"status\":\"INVALID_ARGUMENT\"}}").contentType(MediaType.APPLICATION_JSON));
        s.server().expect(requestTo(ENDPOINT + "/lite:generateContent"))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig").doesNotExist())
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        s.server().expect(requestTo(ENDPOINT + "/lite:generateContent"))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig").doesNotExist())
                .andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        s.gateway().generate(ENDPOINT, "lite", "k", body("one"), false);
        s.gateway().generate(ENDPOINT, "lite", "k", body("two"), false);
        s.server().verify();
    }

    @Test
    void cacheableCallsHitGeminiOnce() {
        Setup s = setup(60, 4, Duration.ofSeconds(1));
        s.server().expect(requestTo(ENDPOINT + "/gemini-embedding-001:embedContent"))
                .andRespond(withSuccess("{\"embedding\":{\"values\":[0.1,0.2]}}", MediaType.APPLICATION_JSON));
        ObjectNode payload = mapper.createObjectNode();
        payload.putObject("content").putArray("parts").addObject().put("text", "same resume");
        JsonNode first = s.gateway().embed(ENDPOINT, "gemini-embedding-001", "k", payload.deepCopy());
        JsonNode second = s.gateway().embed(ENDPOINT, "gemini-embedding-001", "k", payload.deepCopy());
        assertEquals(first, second);
        s.server().verify(); // exactly one request
    }

    @Test
    void failsFastWhenTheMinuteBudgetIsSpent() {
        Setup s = setup(1, 4, Duration.ofMillis(50));
        s.server().expect(requestTo(ENDPOINT + "/m:generateContent")).andRespond(withSuccess(OK, MediaType.APPLICATION_JSON));
        s.gateway().generate(ENDPOINT, "m", "k", body("first"), false);
        assertThrows(GeminiGateway.GeminiBusyException.class, () -> s.gateway().generate(ENDPOINT, "m", "k", body("second"), false));
    }

    @Test
    void honoursRetryAfterAndCapsBackoff() {
        GeminiGateway g = setup(60, 1, Duration.ofSeconds(1)).gateway();
        assertEquals(3000, g.backoffMillis(0, HttpStatus.TOO_MANY_REQUESTS, "3"));
        assertEquals(15_000, g.backoffMillis(0, HttpStatus.TOO_MANY_REQUESTS, "120"));
        assertEquals("27", g.retryDelaySeconds("{\"details\":[{\"retryDelay\": \"27s\"}]}"));
        assertEquals("33", g.retryDelaySeconds("{\"retryDelay\":\"33.5s\"}"));
        assertNull(g.retryDelaySeconds("{}"));
        assertTrue(g.backoffMillis(5, HttpStatus.SERVICE_UNAVAILABLE, null) <= 8_000);
    }

    @Test
    void dailyAllowanceIsPerUserAndResetsNextDay() {
        java.util.concurrent.atomic.AtomicReference<Instant> now = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-09-28T10:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        AiUsageQuota quota = new AiUsageQuota(5, clock);
        quota.consume(1L, 4);
        assertThrows(GeminiApiException.class, () -> quota.consume(1L, 2));
        assertEquals(1, quota.remaining(1L), "a refused request spends nothing");
        quota.consume(2L, 5); // another user has their own allowance
        now.set(Instant.parse("2026-09-29T00:00:01Z"));
        assertEquals(5, quota.remaining(1L));
        quota.consume(1L, 5);   // new day, full allowance again
    }
}
