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
 * candidate's actual resume facts. Mirrors {@link GeminiApplicationPreparationProvider}'s
 * call shape, but asks for a spread of question/answer pairs across categories instead of
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
        if (!"gemini".equalsIgnoreCase(config.getProvider()) || config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("Gemini interview preparation is not configured");
        }
        String prompt = "You are helping a candidate prepare for a real job interview. Return JSON only as "
                + "{questions:[{category,question,suggestedAnswer,sourceEvidence}]}. "
                + "Produce exactly " + count + " items spread across these categories: BEHAVIORAL, TECHNICAL, "
                + "ROLE_SPECIFIC, SITUATIONAL, COMPANY_AND_MOTIVATION. "
                + "Base TECHNICAL and ROLE_SPECIFIC questions on the concrete skills, tools, and responsibilities named in "
                + "JOB_DESCRIPTION, cross-referenced with what the candidate's resume actually shows. "
                + "Every suggestedAnswer must be written in first person as a model answer this candidate could give, using only "
                + "specifics that appear in RESUME_FACTS (real project/experience names, skills, education) -- do not invent metrics, "
                + "employers, or projects the resume does not mention. If a technical question cannot be grounded in the resume, "
                + "still ask it (interviewers ask JD-driven questions regardless), but keep the answer to a general, honest approach "
                + "rather than fabricating personal experience with it. sourceEvidence must quote or closely paraphrase the resume "
                + "text the answer draws on, or be empty string if the answer is a general approach with no resume grounding. "
                + "Never fabricate facts about the candidate.\n"
                + "JOB_DESCRIPTION:\n" + safe(jobDescription) + "\nRESUME_FACTS:\n" + facts;
        ObjectNode body = mapper.createObjectNode();
        body.set("contents", mapper.createArrayNode().add(mapper.createObjectNode().set("parts",
                mapper.createArrayNode().add(mapper.createObjectNode().put("text", prompt)))));
        body.set("generationConfig", mapper.createObjectNode().put("responseMimeType", "application/json"));
        JsonNode root = client.post().uri(config.getEndpoint() + "/" + config.getModel() + ":generateContent?key=" + config.getApiKey())
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
