package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
public class AnthropicClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

    private final WebClient webClient;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String model;

    public AnthropicClient(@Value("${app.anthropic.api-key:}") String apiKey,
                            @Value("${app.anthropic.model:claude-sonnet-4-20250514}") String model,
                            ObjectMapper mapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.anthropic.com/v1")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
        this.mapper = mapper;
    }

    public String complete(String systemPrompt, String userMessage, int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AnthropicApiException(
                    "Claude analysis is not configured on the server (missing ANTHROPIC_API_KEY).",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userMessage))
        );

        JsonNode response = call(body);

        if (response == null || !response.has("content")) {
            throw new AnthropicApiException("Anthropic API returned an empty response.", HttpStatus.BAD_GATEWAY);
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode block : response.get("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        return stripJsonFences(text.toString());
    }

    private JsonNode call(Map<String, Object> body) {
        try {
            return webClient.post()
                    .uri("/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();
        } catch (WebClientResponseException.Unauthorized e) {
            throw new AnthropicApiException(
                    "Anthropic rejected the configured API key (401 Unauthorized). Check ANTHROPIC_API_KEY.",
                    HttpStatus.BAD_GATEWAY);
        } catch (WebClientResponseException.TooManyRequests e) {
            throw new AnthropicApiException(
                    "Anthropic API rate limit reached (429). Please try again in a moment.",
                    HttpStatus.TOO_MANY_REQUESTS);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().is5xxServerError()) {
                throw new AnthropicApiException(
                        "Anthropic API is temporarily unavailable (" + e.getStatusCode().value() + "). Please try again shortly.",
                        HttpStatus.BAD_GATEWAY);
            }
            throw new AnthropicApiException(
                    "Anthropic API request failed (" + e.getStatusCode().value() + ").",
                    HttpStatus.BAD_GATEWAY);
        } catch (WebClientRequestException e) {
            throw new AnthropicApiException(
                    "Could not connect to the Anthropic API. Check server network connectivity.",
                    HttpStatus.BAD_GATEWAY);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof TimeoutException) {
                throw new AnthropicApiException(
                        "Anthropic API request timed out. Please try again.",
                        HttpStatus.GATEWAY_TIMEOUT);
            }
            throw new AnthropicApiException(
                    "Unexpected error calling the Anthropic API.",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    private String stripJsonFences(String raw) {
        return raw.trim()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();
    }
}
