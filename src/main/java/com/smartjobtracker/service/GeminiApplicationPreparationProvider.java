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
public class GeminiApplicationPreparationProvider implements ApplicationPreparationProvider {
    private final AiMatchingConfig config;
    private final RestClient client;
    private final ObjectMapper mapper;

    public GeminiApplicationPreparationProvider(AiMatchingConfig config, RestClient.Builder builder, ObjectMapper mapper) {
        this.config = config;
        this.client = builder.build();
        this.mapper = mapper;
    }

    @Override
    public List<Proposal> suggest(String jobDescription, FactProfile facts, List<FieldRequest> fields) {
        if (!"gemini".equalsIgnoreCase(config.getProvider()) || config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("Gemini application preparation is not configured");
        }
        String prompt = "Return JSON only as {answers:[{externalField,fieldType,value,evidence,rationale}]}. "
                + "Map only requested fields. Every value and evidence must be copied exactly from VERIFIED_FACTS; omit missing facts. "
                + "Never use the job description as an answer source. Never invent, infer, add credentials, bypass CAPTCHA, authenticate, or submit.\n"
                + "REQUESTED_FIELDS:\n" + fields + "\nVERIFIED_FACTS:\n" + facts + "\nJOB_DESCRIPTION:\n" + safe(jobDescription);
        JsonNode body = mapper.createObjectNode();
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).set("contents", mapper.createArrayNode().add(mapper.createObjectNode().set("parts", mapper.createArrayNode().add(mapper.createObjectNode().put("text", prompt)))));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).set("generationConfig", mapper.createObjectNode().put("responseMimeType", "application/json"));
        JsonNode root = client.post().uri(config.getEndpoint() + "/" + config.getModel() + ":generateContent?key=" + config.getApiKey())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        String raw = root == null ? null : root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
        if (raw == null) throw new IllegalStateException("Gemini returned no application answers");
        try {
            List<Proposal> result = new ArrayList<>();
            for (JsonNode item : mapper.readTree(raw.replace("```json", "").replace("```", "")).path("answers")) {
                result.add(new Proposal(item.path("externalField").asText(), com.smartjobtracker.model.ApplicationFieldType.valueOf(item.path("fieldType").asText()), item.path("value").asText(), item.path("evidence").asText(), item.path("rationale").asText()));
            }
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid Gemini application preparation output", ex);
        }
    }

    private String safe(String value) { return value == null ? "" : value.substring(0, Math.min(value.length(), 100000)); }
}
