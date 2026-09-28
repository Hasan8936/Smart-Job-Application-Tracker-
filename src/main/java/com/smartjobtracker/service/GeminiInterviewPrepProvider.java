package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartjobtracker.config.AiMatchingConfig;
import com.smartjobtracker.model.InterviewQuestionCategory;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Generates interview questions + first-person draft answers grounded in the job description and the candidate's
 * own resume text.
 *
 * <p>Requests are split into small per-category batches run in parallel. One call for 50 detailed answers overran
 * both the model's output limit (truncated JSON) and the HTTP read timeout, and every such failure silently fell back
 * to offline templates. A batch that fails is simply missing from the result; the service fills the gap offline and
 * records the session as MIXED. Throws only when every batch fails.
 */
@Component
public class GeminiInterviewPrepProvider implements InterviewPrepProvider {
    private static final Logger log = LoggerFactory.getLogger(GeminiInterviewPrepProvider.class);

    static final int BATCH_SIZE = 8;
    private static final int RESUME_CHARS = 12_000;
    private static final int JD_CHARS = 20_000;
    private static final List<InterviewQuestionCategory> ORDER = List.of(
            InterviewQuestionCategory.BEHAVIORAL, InterviewQuestionCategory.TECHNICAL, InterviewQuestionCategory.ROLE_SPECIFIC,
            InterviewQuestionCategory.SITUATIONAL, InterviewQuestionCategory.COMPANY_AND_MOTIVATION);

    private final AiMatchingConfig config;
    private final RestClient client;
    private final ObjectMapper mapper;
    private final ExecutorService pool = Executors.newFixedThreadPool(5, r -> {
        Thread t = new Thread(r, "interview-prep-gemini");
        t.setDaemon(true);
        return t;
    });

    public GeminiInterviewPrepProvider(AiMatchingConfig config, RestClient.Builder builder, ObjectMapper mapper) {
        this.config = config;
        // Generation is slower than the app-wide 30 s read timeout allows for a batch of long answers.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(90));
        this.client = builder.clone().requestFactory(factory).build();
        this.mapper = mapper;
    }

    @PreDestroy
    void shutdown() { pool.shutdownNow(); }

    @Override
    public List<QuestionAnswer> generate(String jobDescription, FactProfile facts, int count) {
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new IllegalStateException("Gemini API key not set — add AI_MATCHING_API_KEY to your environment");
        }
        List<Batch> batches = plan(count);
        List<Future<List<QuestionAnswer>>> futures = new ArrayList<>();
        for (Batch batch : batches) futures.add(pool.submit(() -> generateBatch(jobDescription, facts, batch)));

        List<QuestionAnswer> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int failed = 0;
        for (int i = 0; i < futures.size(); i++) {
            try {
                for (QuestionAnswer qa : futures.get(i).get(120, TimeUnit.SECONDS)) {
                    if (seen.add(normalize(qa.question()))) result.add(qa);
                }
            } catch (ExecutionException | TimeoutException ex) {
                failed++;
                futures.get(i).cancel(true);
                Throwable cause = ex instanceof ExecutionException ? ex.getCause() : ex;
                log.warn("Interview prep batch {} ({}) failed: {}", i, batches.get(i).category(), describe(cause));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interview preparation was interrupted", ex);
            }
        }
        if (result.isEmpty()) throw new IllegalStateException("All " + batches.size() + " Gemini interview prep batches failed");
        if (failed > 0) log.info("Interview prep: {} of {} Gemini batches failed; the rest will be filled offline", failed, batches.size());
        return result;
    }

    record Batch(InterviewQuestionCategory category, int size, int part, int parts) {}

    /** count split evenly across the five categories, each split into chunks of at most BATCH_SIZE. */
    static List<Batch> plan(int count) {
        List<Batch> batches = new ArrayList<>();
        for (int c = 0; c < ORDER.size(); c++) {
            int n = count / ORDER.size() + (c < count % ORDER.size() ? 1 : 0);
            int parts = (n + BATCH_SIZE - 1) / BATCH_SIZE;
            for (int p = 0; p < parts; p++) {
                int size = n / parts + (p < n % parts ? 1 : 0);
                if (size > 0) batches.add(new Batch(ORDER.get(c), size, p + 1, parts));
            }
        }
        return batches;
    }

    private List<QuestionAnswer> generateBatch(String jobDescription, FactProfile facts, Batch batch) {
        String prompt = prompt(jobDescription, facts, batch);
        ObjectNode body = mapper.createObjectNode();
        body.set("contents", mapper.createArrayNode().add(mapper.createObjectNode().set("parts",
                mapper.createArrayNode().add(mapper.createObjectNode().put("text", prompt)))));
        body.set("generationConfig", mapper.createObjectNode()
                .put("responseMimeType", "application/json")
                .put("temperature", 0.5)
                .put("maxOutputTokens", 8192));
        JsonNode root = client.post().uri(config.getEndpoint() + "/" + config.getInterviewModel() + ":generateContent?key=" + config.getApiKey())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        JsonNode candidate = root == null ? null : root.path("candidates").path(0);
        String raw = candidate == null ? null : candidate.path("content").path("parts").path(0).path("text").asText(null);
        if (raw == null) throw new IllegalStateException("Gemini returned no content (finishReason=" + (candidate == null ? "none" : candidate.path("finishReason").asText("unknown")) + ")");
        try {
            List<QuestionAnswer> result = new ArrayList<>();
            for (JsonNode item : mapper.readTree(raw.replace("```json", "").replace("```", "")).path("questions")) {
                String question = item.path("question").asText("").trim();
                String answer = item.path("suggestedAnswer").asText("").trim();
                if (question.isEmpty() || answer.isEmpty()) continue;
                result.add(new QuestionAnswer(batch.category(), question, answer, item.path("sourceEvidence").asText("").trim()));
            }
            if (result.isEmpty()) throw new IllegalStateException("Gemini returned zero interview questions");
            return result;
        } catch (IllegalStateException ex) { throw ex; }
        catch (Exception ex) {
            throw new IllegalStateException("Invalid Gemini interview output (finishReason=" + candidate.path("finishReason").asText("unknown") + ")", ex);
        }
    }

    String prompt(String jobDescription, FactProfile facts, Batch batch) {
        String resume = facts == null ? "" : bounded(facts.resumeText(), RESUME_CHARS);
        if (resume.isBlank() && facts != null) {
            resume = "Experience:\n" + nz(facts.experience()) + "\nProjects:\n" + nz(facts.projects())
                    + "\nSkills: " + nz(facts.skills()) + "\nEducation:\n" + nz(facts.education());
        }
        return "You are an expert interview coach preparing a specific candidate for a real interview for the job below.\n"
                + "Return ONLY a JSON object (no markdown) of this exact shape:\n"
                + "{\"questions\":[{\"question\":\"...\",\"suggestedAnswer\":\"...\",\"sourceEvidence\":\"...\"}]}\n\n"
                + "Write EXACTLY " + batch.size() + " " + describe(batch.category()) + " questions"
                + (batch.parts() > 1 ? " (this is batch " + batch.part() + " of " + batch.parts() + " for this category — cover different topics than a generic first batch would, e.g. vary which responsibilities and skills you draw on)" : "")
                + ".\n\nRules for every suggestedAnswer:\n"
                + "- Write the answer itself, in first person, exactly as the candidate would say it out loud: 90-160 words.\n"
                + "- Build it from concrete material in RESUME: name the actual employer or project, the tools used, what the candidate did, and the numbers/results stated there.\n"
                + "- NEVER write advice about answering (no \"use the STAR method\", \"mention...\", \"talk about...\", \"highlight...\"). The candidate needs a model answer, not instructions.\n"
                + "- Never invent employers, projects, tools, metrics, dates or experience that are not in RESUME. If RESUME has nothing relevant, say so honestly in the answer and describe the concrete approach the candidate would take.\n"
                + "- For conflict, mistake or feedback questions the resume won't contain the story: set the answer in a real project from RESUME and put the personal details the candidate must fill in inside [square brackets].\n"
                + "- sourceEvidence: quote the RESUME line(s) the answer relies on, or \"\" if none.\n\n"
                + "Rules for questions: specific to THIS job's responsibilities, stack and seniority; realistic and probing, the way a strong interviewer would ask; no duplicates.\n\n"
                + "JOB_DESCRIPTION:\n" + bounded(jobDescription, JD_CHARS) + "\n\nRESUME:\n" + resume;
    }

    private static String describe(InterviewQuestionCategory category) {
        return switch (category) {
            case BEHAVIORAL -> "BEHAVIORAL (past situations; answers told as a short story: situation, what I did, result)";
            case TECHNICAL -> "TECHNICAL (the specific languages, frameworks, tools and concepts this job lists; include at least one deeper follow-up style question)";
            case ROLE_SPECIFIC -> "ROLE-SPECIFIC (the day-to-day responsibilities named in the job description)";
            case SITUATIONAL -> "SITUATIONAL (concrete hypothetical scenarios this role will face; answers give the approach, backed by a real example from RESUME when one fits)";
            case COMPANY_AND_MOTIVATION -> "COMPANY AND MOTIVATION (why this role, fit, goals; ground answers in what the job description says and the candidate's real experience, never in invented facts about the company)";
        };
    }

    private static String describe(Throwable t) {
        if (t instanceof RestClientResponseException r) return "HTTP " + r.getStatusCode().value();
        return t == null ? "unknown" : t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    private static String normalize(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ""); }
    private static String nz(String s) { return s == null ? "" : s; }
    private static String bounded(String value, int max) { return value == null ? "" : value.substring(0, Math.min(value.length(), max)); }
}
