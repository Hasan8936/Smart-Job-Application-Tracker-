package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.config.AiMatchingConfig;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.ArrayList;
import java.util.List;

@Component
public class GeminiResumeTailoringProvider implements ResumeTailoringProvider {
    private final AiMatchingConfig config;
    private final RestClient client;
    private final ObjectMapper mapper;

    public GeminiResumeTailoringProvider(AiMatchingConfig config, RestClient.Builder builder, ObjectMapper mapper) {
        this.config = config; this.client = builder.build(); this.mapper = mapper;
    }

    @Override
    public List<Proposal> suggest(String resumeText, String jobDescription, List<String> atsKeywords) {
        if (!"gemini".equalsIgnoreCase(config.getProvider()) || config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("Gemini tailoring is not configured");
        }
        String prompt = "Return JSON only as {suggestions:[{category,beforeText,afterText,rationale,evidenceText}]}. "
                + "Only suggest rewrites of text literally present in ORIGINAL_RESUME. Never add a skill, metric, employer, title, date, project, or achievement. "
                + "beforeText and evidenceText must be exact excerpts from ORIGINAL_RESUME. afterText may only reorder or clarify words already present in ORIGINAL_RESUME. "
                + "ATS keywords are recommendations only and must not be inserted unless already evidenced.\nATS_KEYWORDS:\n"
                + String.join(", ", atsKeywords) + "\nORIGINAL_RESUME:\n" + safe(resumeText) + "\nJOB_DESCRIPTION:\n" + safe(jobDescription);
        JsonNode body = mapper.createObjectNode();
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).set("contents", mapper.createArrayNode().add(mapper.createObjectNode().set("parts", mapper.createArrayNode().add(mapper.createObjectNode().put("text", prompt)))));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).set("generationConfig", mapper.createObjectNode().put("responseMimeType", "application/json"));
        JsonNode root = client.post().uri(config.getEndpoint() + "/" + config.getModel() + ":generateContent?key=" + config.getApiKey())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        String raw = root == null ? null : root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
        if (raw == null) throw new IllegalStateException("Gemini returned no tailoring suggestions");
        try {
            JsonNode suggestions = mapper.readTree(raw.replace("```json", "").replace("```", "")).path("suggestions");
            List<Proposal> result = new ArrayList<>();
            for (JsonNode item : suggestions) result.add(new Proposal(text(item, "category"), text(item, "beforeText"), text(item, "afterText"), text(item, "rationale"), text(item, "evidenceText")));
            return result;
        } catch (Exception ex) { throw new IllegalStateException("Invalid Gemini tailoring output", ex); }
    }

    private String text(JsonNode node, String name) { return node.path(name).asText("").trim(); }
    private String safe(String value) { return value == null ? "" : value.substring(0, Math.min(value.length(), 100000)); }
}