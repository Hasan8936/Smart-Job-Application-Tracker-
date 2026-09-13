package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartjobtracker.config.AiMatchingConfig;
import com.smartjobtracker.model.InterviewQuestionCategory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates interview questions + model answers grounded in the job description and the
 * candidate's actual resume facts. Uses the same Gemini call shape as the app's other AI-backed
 * providers, but asks for a spread of question/answer pairs across categories instead of
 * form-field values.
 */
@Component
public class GeminiInterviewPrepProvider implements InterviewPrepProvider {
    private final AiMatchingConfig config;
    private final RestClient client;
    private final ObjectMapper mapper;

    public GeminiInterviewPrepProvider(AiMatchingConfig config, RestClient.Builder builder, ObjectMapper mapper) {
        this.config = config;
        this.client = builder.build();
        this.mapper = mapper;
    }

    @Override
    public List<QuestionAnswer> generate(String jobDescription, FactProfile facts, int count) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("Gemini API key not set — add AI_MATCHING_API_KEY to your environment");
        }
        String prompt = "You are an expert interview coach helping a job seeker prepare for a real interview.\n"
                + "Return ONLY a valid JSON object (no markdown, no code fences) in this exact shape:\n"
                + "{\"questions\":[{\"category\":\"BEHAVIORAL\",\"question\":\"...\",\"suggestedAnswer\":\"...\",\"sourceEvidence\":\"...\"}]}\n\n"
                + "Rules:\n"
                + "1. Generate EXACTLY " + count + " questions, split approximately evenly across all five categories:\n"
                + "   BEHAVIORAL, TECHNICAL, ROLE_SPECIFIC, SITUATIONAL, COMPANY_AND_MOTIVATION\n"
                + "2. TECHNICAL and ROLE_SPECIFIC questions MUST be grounded in specific skills, tools, frameworks, "
                + "and responsibilities mentioned in the JOB_DESCRIPTION below.\n"
                + "3. Every suggestedAnswer MUST be written in first person (\"I...\"), using only facts that appear "
                + "in RESUME_FACTS. Never invent employers, project names, metrics, or skills not in the resume.\n"
                + "4. If a TECHNICAL question asks about something NOT in the resume, still include the question "
                + "(interviewers will ask it) but write the answer as an honest general approach.\n"
                + "5. sourceEvidence: quote or closely paraphrase the resume text the answer draws from. "
                + "Use empty string \"\" if the answer is a general approach.\n"
                + "6. Make questions specific, challenging, and realistic — avoid generic questions like "
                + "\"Tell me about yourself\" unless they are particularly relevant.\n"
                + "7. BEHAVIORAL questions should follow the STAR format (Situation, Task, Action, Result).\n"
                + "8. SITUATIONAL questions should present a concrete scenario the candidate might face in this role.\n\n"
                + "JOB_DESCRIPTION:\n" + safe(jobDescription) + "\n\nRESUME_FACTS:\n" + facts;
        ObjectNode body = mapper.createObjectNode();
        body.set("contents", mapper.createArrayNode().add(mapper.createObjectNode().set("parts",
                mapper.createArrayNode().add(mapper.createObjectNode().put("text", prompt)))));
        body.set("generationConfig", mapper.createObjectNode()
                .put("responseMimeType", "application/json")
                .put("temperature", 0.7));
        JsonNode root = client.post().uri(config.getEndpoint() + "/" + config.getInterviewModel() + ":generateContent?key=" + config.getApiKey())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        String raw = root == null ? null : root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
        if (raw == null) throw new IllegalStateException("Gemini returned no interview questions");
        try {
            List<QuestionAnswer> result = new ArrayList<>();
            for (JsonNode item : mapper.readTree(raw.replace("```json", "").replace("```", "")).path("questions")) {
                InterviewQuestionCategory category;
                try { category = InterviewQuestionCategory.valueOf(item.path("category").asText()); }
                catch (IllegalArgumentException ex) { category = InterviewQuestionCategory.ROLE_SPECIFIC; }
                result.add(new QuestionAnswer(category, item.path("question").asText(), item.path("suggestedAnswer").asText(), item.path("sourceEvidence").asText("")));
            }
            if (result.isEmpty()) throw new IllegalStateException("Gemini returned zero interview questions");
            return result;
        } catch (IllegalStateException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("Invalid Gemini interview preparation output", ex); }
    }

    private String safe(String value) { return value == null ? "" : value.substring(0, Math.min(value.length(), 100000)); }
}
