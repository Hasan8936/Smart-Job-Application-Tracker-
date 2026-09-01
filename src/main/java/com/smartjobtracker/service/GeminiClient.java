package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartjobtracker.config.GmailConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Same role AnthropicClient used to play for resume deep-match: a single-call
 * "system prompt + user message in, text out" client. Reuses the Gemini credentials
 * already configured for Gmail email classification (GMAIL_CLASSIFICATION_PROVIDER/
 * API_KEY/MODEL/ENDPOINT) rather than app.ai-matching, which GeminiSemanticSimilarityProvider
 * (embeddings) and GeminiResumeTailoringProvider (generation) already share one model value
 * between — adding a third, differently-shaped use onto that config would make an existing
 * ambiguity worse. This mirrors GeminiTelegramPostExtractor's choice to reuse the same
 * classification config for the same reason.
 */
@Component
public class GeminiClient {

    private final GmailConfig config;
    private final RestClient client;
    private final ObjectMapper mapper;

    public GeminiClient(GmailConfig config, RestClient.Builder builder, ObjectMapper mapper) {
        this.config = config;
        this.client = builder.build();
        this.mapper = mapper;
    }

    public String complete(String systemPrompt, String userMessage, int maxTokens) {
        if (!"gemini".equalsIgnoreCase(config.getClassificationProvider())
                || config.getClassificationApiKey() == null || config.getClassificationApiKey().isBlank()) {
            throw new GeminiApiException(
                    "AI analysis is not configured on the server (missing GMAIL_CLASSIFICATION_API_KEY).",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        ObjectNode body = mapper.createObjectNode();
        body.set("systemInstruction", mapper.createObjectNode().set("parts",
                mapper.createArrayNode().add(mapper.createObjectNode().put("text", systemPrompt))));
        body.set("contents", mapper.createArrayNode().add(mapper.createObjectNode().set("parts",
                mapper.createArrayNode().add(mapper.createObjectNode().put("text", userMessage)))));
        body.set("generationConfig", mapper.createObjectNode()
                .put("responseMimeType", "application/json")
                .put("maxOutputTokens", maxTokens));

        JsonNode response = call(body);

        String raw = response == null ? null
                : response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
        if (raw == null || raw.isBlank()) {
            throw new GeminiApiException("Gemini API returned an empty response.", HttpStatus.BAD_GATEWAY);
        }
        return stripJsonFences(raw);
    }

    private JsonNode call(ObjectNode body) {
        try {
            return client.post()
                    .uri(config.getClassificationEndpoint() + "/" + config.getClassificationModel()
                            + ":generateContent?key=" + config.getClassificationApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                throw new GeminiApiException(
                        "Gemini rejected the configured API key (" + e.getStatusCode().value()
                                + "). Check GMAIL_CLASSIFICATION_API_KEY.",
                        HttpStatus.BAD_GATEWAY);
            }
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                throw new GeminiApiException(
                        "Gemini API rate limit reached (429). Please try again in a moment.",
                        HttpStatus.TOO_MANY_REQUESTS);
            }
            if (e.getStatusCode().is5xxServerError()) {
                throw new GeminiApiException(
                        "Gemini API is temporarily unavailable (" + e.getStatusCode().value() + "). Please try again shortly.",
                        HttpStatus.BAD_GATEWAY);
            }
            String body = e.getResponseBodyAsString();
            String detail = body == null || body.isBlank() ? "" : ": " + body.substring(0, Math.min(300, body.length()));
            throw new GeminiApiException(
                    "Gemini API request failed (" + e.getStatusCode().value() + ")" + detail,
                    HttpStatus.BAD_GATEWAY);
        } catch (RuntimeException e) {
            throw new GeminiApiException("Could not connect to the Gemini API. Check server network connectivity.",
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
