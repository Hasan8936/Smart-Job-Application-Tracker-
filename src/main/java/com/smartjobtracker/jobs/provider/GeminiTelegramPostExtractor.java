package com.smartjobtracker.jobs.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.config.GmailConfig;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Reuses the exact same Gemini credentials/model/endpoint already configured for Gmail email
 * classification (GMAIL_CLASSIFICATION_PROVIDER/API_KEY/MODEL/ENDPOINT) — this is genuinely
 * the same "extract structured fields from free text with an LLM" capability, just applied to
 * a different text source, so no second AI provider config is introduced for this feature.
 * Mirrors GeminiEmailClassifier's request/response shape closely.
 */
@Component
public class GeminiTelegramPostExtractor implements TelegramPostExtractor {
    private final GmailConfig config;
    private final RestClient client;
    private final ObjectMapper mapper;

    public GeminiTelegramPostExtractor(GmailConfig config, RestClient.Builder builder, ObjectMapper mapper) {
        this.config = config;
        this.client = builder.build();
        this.mapper = mapper;
    }

    @Override
    public Extraction extract(Input input) {
        if (!"gemini".equalsIgnoreCase(config.getClassificationProvider())
                || config.getClassificationApiKey() == null || config.getClassificationApiKey().isBlank()) {
            throw new IllegalStateException("Gemini extraction is not configured");
        }
        String prompt = "Return JSON only with keys company,title,location,employmentType,applyUrl,confidence. "
                + "This text is a message from a Telegram channel that sometimes posts job openings and sometimes "
                + "posts unrelated content (announcements, discussion, memes). "
                + "If this specific message is NOT a genuine job posting, return confidence 0 and null for every "
                + "other field. If it is a job posting, extract only what the text actually states — never invent "
                + "a company, title, or location that isn't clearly present. employmentType must be one of "
                + "FULL_TIME, PART_TIME, INTERNSHIP, CONTRACT, or null. applyUrl must be chosen from the provided "
                + "LINKS_FOUND list when possible (pick the one most likely to be the application link), or null "
                + "if none look like an application link. confidence is 0 to 1, reflecting how sure you are this "
                + "is a real, specific job opening (not a generic announcement about hiring in general).\n"
                + "LINKS_FOUND: " + (input.links() == null ? "[]" : input.links()) + "\n"
                + "MESSAGE:\n" + safe(input.text());

        JsonNode payload = mapper.createObjectNode().put("contents", "");
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).set("contents",
                mapper.createArrayNode().add(mapper.createObjectNode().set("parts",
                        mapper.createArrayNode().add(mapper.createObjectNode().put("text", prompt)))));
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).set("generationConfig",
                mapper.createObjectNode().put("responseMimeType", "application/json"));

        JsonNode root = client.post()
                .uri(config.getClassificationEndpoint() + "/" + config.getClassificationModel() + ":generateContent?key=" + config.getClassificationApiKey())
                .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(JsonNode.class);
        String raw = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
        if (raw == null) throw new IllegalStateException("Gemini returned no extraction");
        try {
            return parse(mapper.readTree(raw.replace("```json", "").replace("```", "")));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid Gemini extraction JSON", ex);
        }
    }

    private Extraction parse(JsonNode node) {
        double confidence = node.path("confidence").asDouble(-1);
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("Invalid Telegram extraction confidence");
        }
        String employmentType = text(node, "employmentType");
        if (employmentType != null && !List.of("FULL_TIME", "PART_TIME", "INTERNSHIP", "CONTRACT").contains(employmentType)) {
            employmentType = null;
        }
        return new Extraction(text(node, "company"), text(node, "title"), text(node, "location"),
                employmentType, text(node, "applyUrl"), confidence, "gemini");
    }

    private String text(JsonNode node, String name) {
        if (!node.hasNonNull(name)) return null;
        String value = node.get(name).asText();
        return value.isBlank() ? null : value.length() > 300 ? value.substring(0, 300) : value;
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("(?i)(token|password|authorization)\\s*[:=]\\s*\\S+", "$1=[REDACTED]")
                .substring(0, Math.min(value.length(), 2000));
    }
}
