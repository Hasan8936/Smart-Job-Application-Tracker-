package com.smartjobtracker.jobs.match;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.config.AiMatchingConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

@Component
public class GeminiSemanticSimilarityProvider implements SemanticSimilarityProvider {
    private final AiMatchingConfig config; private final RestClient client; private final ObjectMapper mapper;
    public GeminiSemanticSimilarityProvider(AiMatchingConfig config, RestClient.Builder builder, ObjectMapper mapper) {
        this.config = config; this.client = builder.build(); this.mapper = mapper;
    }
    @Override public OptionalDouble similarity(String resumeText, String jobText) {
        if (!"gemini".equalsIgnoreCase(config.getProvider()) || config.getApiKey() == null || config.getApiKey().isBlank()) return OptionalDouble.empty();
        try {
            List<List<Double>> vectors = new ArrayList<>();
            for (String text : List.of(resumeText == null ? "" : resumeText, jobText == null ? "" : jobText)) {
                var parts = mapper.createArrayNode();
                parts.add(mapper.createObjectNode().put("text", text));
                var content = mapper.createObjectNode().set("parts", parts);
                var payload = mapper.createObjectNode().set("content", content);
                JsonNode root = client.post().uri(config.getEndpoint() + "/" + config.getModel() + ":embedContent?key=" + config.getApiKey())
                    .body(payload)
                        .retrieve().body(JsonNode.class);
                JsonNode values = root.path("embedding").path("values");
                if (!values.isArray() || values.isEmpty()) return OptionalDouble.empty();
                List<Double> vector = new ArrayList<>(); values.forEach(node -> vector.add(node.isNumber() ? node.asDouble() : Double.NaN)); vectors.add(vector);
            }
            double score = cosine(vectors.get(0), vectors.get(1));
            return Double.isFinite(score) && score >= 0 && score <= 1 ? OptionalDouble.of(score) : OptionalDouble.empty();
        } catch (RuntimeException ex) { return OptionalDouble.empty(); }
    }
    private double cosine(List<Double> a, List<Double> b) {
        if (a.size() != b.size() || a.isEmpty()) return Double.NaN;
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.size(); i++) { if (!Double.isFinite(a.get(i)) || !Double.isFinite(b.get(i))) return Double.NaN; dot += a.get(i) * b.get(i); aa += a.get(i) * a.get(i); bb += b.get(i) * b.get(i); }
        if (aa == 0 || bb == 0) return 0; return Math.max(0, Math.min(1, dot / (Math.sqrt(aa) * Math.sqrt(bb))));
    }
}