package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * The one path every Gemini call takes, so a single API key can serve many users without tripping its quota:
 * <ul>
 *   <li>the key goes in the {@code x-goog-api-key} header — never the URL, where I/O-error messages would log it;</li>
 *   <li>an account-wide requests-per-minute limit and a cap on requests in flight; a caller that can't get a slot within
 *       {@code queue-wait} gets {@link GeminiBusyException} fast and uses its offline fallback instead of piling up;</li>
 *   <li>429 / 5xx are retried with exponential backoff and jitter, honouring {@code Retry-After};</li>
 *   <li>"thinking" is off by default ({@code thinkingBudget}); it was ~140 hidden tokens even for a one-word reply.
 *       Models that reject the setting (flash-lite) are remembered and sent without it;</li>
 *   <li>deterministic calls (embeddings, classification) can be served from a small in-memory LRU cache.</li>
 * </ul>
 * Logs model, status, attempts and latency only — never prompts, responses or the key.
 */
@Component
public class GeminiGateway {
    private static final Logger log = LoggerFactory.getLogger(GeminiGateway.class);
    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);

    /** Sleeps between retries; swapped out in tests. */
    interface Sleeper { void sleep(long millis) throws InterruptedException; }

    private final RestClient client;
    private final ObjectMapper mapper;
    private final int maxRetries;
    private final int thinkingBudget;
    private final long queueWaitMillis;
    private final long cacheTtlMillis;
    private final int cacheMaxEntries;
    private final Semaphore inFlight;
    private final RequestsPerMinute rpm;
    private final Sleeper sleeper;
    private final Set<String> modelsWithoutThinkingConfig = ConcurrentHashMap.newKeySet();
    private final Map<String, CachedResponse> cache;

    private record CachedResponse(JsonNode body, long expiresAt) {}

    @Autowired
    public GeminiGateway(RestClient.Builder builder, ObjectMapper mapper,
                         @Value("${app.gemini.requests-per-minute:10}") int requestsPerMinute,
                         @Value("${app.gemini.max-concurrent:4}") int maxConcurrent,
                         @Value("${app.gemini.queue-wait:PT20S}") Duration queueWait,
                         @Value("${app.gemini.max-retries:2}") int maxRetries,
                         @Value("${app.gemini.thinking-budget:0}") int thinkingBudget,
                         @Value("${app.gemini.cache-ttl:PT12H}") Duration cacheTtl,
                         @Value("${app.gemini.cache-max-entries:2000}") int cacheMaxEntries) {
        this(timeoutClient(builder), mapper, requestsPerMinute, maxConcurrent, queueWait, maxRetries, thinkingBudget,
                cacheTtl, cacheMaxEntries, Thread::sleep);
    }

    GeminiGateway(RestClient client, ObjectMapper mapper, int requestsPerMinute, int maxConcurrent, Duration queueWait,
                  int maxRetries, int thinkingBudget, Duration cacheTtl, int cacheMaxEntries, Sleeper sleeper) {
        this.client = client;
        this.mapper = mapper;
        this.maxRetries = Math.max(0, maxRetries);
        this.thinkingBudget = thinkingBudget;
        this.queueWaitMillis = queueWait.toMillis();
        this.cacheTtlMillis = cacheTtl.toMillis();
        this.cacheMaxEntries = cacheMaxEntries;
        this.inFlight = new Semaphore(Math.max(1, maxConcurrent), true);
        this.rpm = new RequestsPerMinute(Math.max(1, requestsPerMinute));
        this.sleeper = sleeper;
        this.cache = new LinkedHashMap<>(256, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, CachedResponse> eldest) { return size() > GeminiGateway.this.cacheMaxEntries; }
        };
    }

    /** Generation calls read slowly; the app-wide 30 s read timeout is too short for long answers. */
    private static RestClient timeoutClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(90));
        return builder.clone().requestFactory(factory).build();
    }

    /** {@code :generateContent}. Adds the thinking budget unless the body sets one or the model rejects it. */
    public JsonNode generate(String endpoint, String model, String apiKey, ObjectNode body, boolean cacheable) {
        return call(endpoint, model, "generateContent", apiKey, body, cacheable, true);
    }

    /** {@code :embedContent}. Always cacheable: the same resume or job text embeds to the same vector. */
    public JsonNode embed(String endpoint, String model, String apiKey, ObjectNode body) {
        return call(endpoint, model, "embedContent", apiKey, body, true, false);
    }

    private JsonNode call(String endpoint, String model, String method, String apiKey, ObjectNode body,
                          boolean cacheable, boolean isGeneration) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("Gemini API key is not configured");
        if (isGeneration) applyThinkingBudget(model, body);
        String key = cacheable ? cacheKey(model, method, body) : null;
        if (key != null) {
            JsonNode hit = cached(key);
            if (hit != null) return hit;
        }
        String url = endpoint + "/" + model + ":" + method;
        acquireSlot(model);
        long started = System.nanoTime();
        try {
            for (int attempt = 0; ; attempt++) {
                try {
                    JsonNode response = client.post().uri(url)
                            .header("x-goog-api-key", apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body).retrieve().body(JsonNode.class);
                    if (key != null && response != null) store(key, response);
                    log.debug("Gemini {} {} ok attempts={} ms={}", model, method, attempt + 1, (System.nanoTime() - started) / 1_000_000);
                    return response;
                } catch (RestClientResponseException ex) {
                    int status = ex.getStatusCode().value();
                    if (status == 400 && isGeneration && body.path("generationConfig").has("thinkingConfig")
                            && ex.getResponseBodyAsString().contains("INVALID_ARGUMENT") && modelsWithoutThinkingConfig.add(model)) {
                        ((ObjectNode) body.path("generationConfig")).remove("thinkingConfig");
                        log.info("Gemini model {} rejects thinkingConfig; sending without it from now on", model);
                        attempt--; // not a real failure
                        continue;
                    }
                    String errorBody = ex.getResponseBodyAsString();
                    if (status == 429) logQuota(model, errorBody);
                    if (!RETRYABLE.contains(status) || attempt >= maxRetries) {
                        log.warn("Gemini {} {} failed: HTTP {} after {} attempt(s)", model, method, status, attempt + 1);
                        throw ex;
                    }
                    String retryAfter = retryDelaySeconds(errorBody);
                    if (retryAfter == null && ex.getResponseHeaders() != null) retryAfter = ex.getResponseHeaders().getFirst("Retry-After");
                    long wait = backoffMillis(attempt, ex.getStatusCode(), retryAfter);
                    log.info("Gemini {} {} got HTTP {}; retry {} in {} ms", model, method, status, attempt + 1, wait);
                    sleeper.sleep(wait);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GeminiBusyException("Interrupted while waiting to retry Gemini");
        } finally {
            inFlight.release();
        }
    }

    private void applyThinkingBudget(String model, ObjectNode body) {
        if (thinkingBudget < 0 || modelsWithoutThinkingConfig.contains(model)) return;
        ObjectNode generationConfig = body.has("generationConfig") && body.get("generationConfig").isObject()
                ? (ObjectNode) body.get("generationConfig") : body.putObject("generationConfig");
        if (!generationConfig.has("thinkingConfig")) generationConfig.putObject("thinkingConfig").put("thinkingBudget", thinkingBudget);
    }

    private void acquireSlot(String model) {
        long deadline = System.currentTimeMillis() + queueWaitMillis;
        try {
            if (!inFlight.tryAcquire(queueWaitMillis, TimeUnit.MILLISECONDS)) throw busy(model, "concurrency");
            long remaining = deadline - System.currentTimeMillis();
            if (!rpm.tryAcquire(Math.max(0, remaining))) {
                inFlight.release();
                throw busy(model, "requests-per-minute");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GeminiBusyException("Interrupted while waiting for a Gemini slot");
        }
    }

    private GeminiBusyException busy(String model, String limit) {
        log.warn("Gemini {} busy ({} limit); caller will use its fallback", model, limit);
        return new GeminiBusyException("AI is busy right now (" + limit + " limit). Please try again shortly.");
    }

    /**
     * Gemini's 429 body carries a RetryInfo ("retryDelay": "27s") and a QuotaFailure naming the exhausted quota.
     * Waiting as long as it asks beats a 1–2 s blind retry that is sure to be refused again.
     */
    String retryDelaySeconds(String errorBody) {
        if (errorBody == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)(?:\\.\\d+)?s\"").matcher(errorBody);
        return m.find() ? m.group(1) : null;
    }

    private final Set<String> loggedQuotas = ConcurrentHashMap.newKeySet();

    /** Logs which quota was hit (e.g. "...PerMinutePerProjectPerModel-FreeTier" = 10) once, so the tier is visible. */
    private void logQuota(String model, String errorBody) {
        if (errorBody == null) return;
        java.util.regex.Matcher id = java.util.regex.Pattern.compile("\"quotaId\"\\s*:\\s*\"([^\"]+)\"").matcher(errorBody);
        java.util.regex.Matcher value = java.util.regex.Pattern.compile("\"quotaValue\"\\s*:\\s*\"?(\\d+)").matcher(errorBody);
        String quotaId = id.find() ? id.group(1) : "unknown";
        String quotaValue = value.find() ? value.group(1) : "?";
        if (loggedQuotas.add(model + "|" + quotaId)) {
            log.warn("Gemini quota hit for {}: {} = {}. Set GEMINI_REQUESTS_PER_MINUTE at or below the per-minute value.", model, quotaId, quotaValue);
        }
    }

    long backoffMillis(int attempt, HttpStatusCode status, String retryAfter) {
        if (retryAfter != null) {
            try { return Math.min(15_000, Long.parseLong(retryAfter.trim()) * 1000); } catch (NumberFormatException ignored) { }
        }
        long base = 1000L << attempt;                        // 1 s, 2 s, 4 s ...
        return Math.min(8_000, base + ThreadLocalRandom.current().nextLong(250));
    }

    private String cacheKey(String model, String method, ObjectNode body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((model + "|" + method + "|" + mapper.writeValueAsString(body)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            return null;
        }
    }

    private synchronized JsonNode cached(String key) {
        CachedResponse c = cache.get(key);
        if (c == null) return null;
        if (c.expiresAt() < System.currentTimeMillis()) { cache.remove(key); return null; }
        return c.body();
    }

    private synchronized void store(String key, JsonNode body) {
        cache.put(key, new CachedResponse(body, System.currentTimeMillis() + cacheTtlMillis));
    }

    /** Sliding one-minute window shared by every caller of the key. */
    static final class RequestsPerMinute {
        private final int limit;
        private final long[] stamps;
        private int next;

        RequestsPerMinute(int limit) { this.limit = limit; this.stamps = new long[limit]; }

        boolean tryAcquire(long maxWaitMillis) throws InterruptedException {
            long deadline = System.currentTimeMillis() + maxWaitMillis;
            while (true) {
                long waitFor;
                synchronized (this) {
                    long now = System.currentTimeMillis();
                    long oldest = stamps[next];                // slot reused once `limit` calls have happened
                    if (oldest == 0 || now - oldest >= 60_000) {
                        stamps[next] = now;
                        next = (next + 1) % limit;
                        return true;
                    }
                    waitFor = 60_000 - (now - oldest);
                }
                if (System.currentTimeMillis() + waitFor > deadline) return false;
                Thread.sleep(Math.min(waitFor, 1000));
            }
        }
    }

    /** Thrown when no request slot frees up in time; callers treat it like any Gemini failure and fall back. */
    public static class GeminiBusyException extends RuntimeException {
        public GeminiBusyException(String message) { super(message); }
    }
}
