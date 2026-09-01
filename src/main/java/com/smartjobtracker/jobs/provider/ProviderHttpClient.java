package com.smartjobtracker.jobs.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

public class ProviderHttpClient {
    private final RestClient client;
    private final long minIntervalMs;
    private final int maxRetries;
    private long nextRequestAt;

    public ProviderHttpClient(RestClient.Builder builder, long minIntervalMs, int maxRetries) {
        client = builder.build();
        this.minIntervalMs = Math.max(0, minIntervalMs);
        this.maxRetries = Math.max(0, maxRetries);
    }

    public JsonNode get(String uri) {
        return execute(() -> client.get().uri(uri).retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new ProviderHttpException(response.getStatusCode().value());
                }).body(JsonNode.class));
    }

    public JsonNode post(String uri, JsonNode body) {
        return execute(() -> client.post().uri(uri).body(body).retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new ProviderHttpException(response.getStatusCode().value());
                }).body(JsonNode.class));
    }

    /** Raw text/HTML fetch (e.g. Telegram's public channel preview page), sharing the same rate-limit and retry behavior as get()/post(). */
    public String getHtml(String uri) {
        return execute(() -> client.get().uri(uri).retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new ProviderHttpException(response.getStatusCode().value());
                }).body(String.class));
    }

    private synchronized <T> T execute(Supplier<T> request) {
        for (int attempt = 0; ; attempt++) {
            waitForRateLimit();
            try {
                return request.get();
            } catch (ProviderHttpException | RestClientResponseException ex) {
                if (attempt >= maxRetries || !isRetryable(ex)) {
                    String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    throw new ProviderUnavailableException("Job provider request failed: " + detail, ex);
                }
                long delay = Math.min(10_000L, 250L * (1L << Math.min(attempt, 5)));
                try { Thread.sleep(delay); } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ProviderUnavailableException("Job provider request interrupted", interrupted);
                }
            }
        }
    }

    private void waitForRateLimit() {
        long wait = nextRequestAt - System.currentTimeMillis();
        if (wait > 0) {
            try { Thread.sleep(wait); } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new ProviderUnavailableException("Job provider request interrupted", interrupted);
            }
        }
        nextRequestAt = System.currentTimeMillis() + minIntervalMs;
    }

    private boolean isRetryable(Exception ex) {
        if (ex instanceof ProviderHttpException provider) {
            return provider.status >= 500 || provider.status == 429;
        }
        RestClientResponseException response = (RestClientResponseException) ex;
        return response.getStatusCode().is5xxServerError() || response.getStatusCode().value() == 429;
    }

    private static class ProviderHttpException extends RuntimeException {
        private final int status;
        private ProviderHttpException(int status) { super("Provider responded with HTTP " + status); this.status = status; }
    }

    public static class ProviderUnavailableException extends RuntimeException {
        public ProviderUnavailableException(String message, Throwable cause) { super(message, cause); }
        public ProviderUnavailableException(String message) { super(message); }
    }
}