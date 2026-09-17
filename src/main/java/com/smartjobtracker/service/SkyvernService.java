package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.CandidateProfileRepository;
import com.smartjobtracker.repository.JobPostingRepository;
import com.smartjobtracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Map;

@Service
public class SkyvernService {
    private static final Logger log = LoggerFactory.getLogger(SkyvernService.class);

    @Value("${SKYVERN_API_URL:}")
    private String skyvernApiUrl;

    @Value("${SKYVERN_API_KEY:}")
    private String skyvernApiKey;

    private final JobPostingRepository jobRepository;
    private final UserRepository userRepository;
    private final CandidateProfileRepository profileRepository;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public SkyvernService(JobPostingRepository jobRepository, UserRepository userRepository,
                          CandidateProfileRepository profileRepository, ObjectMapper objectMapper,
                          RestClient.Builder builder) {
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.objectMapper = objectMapper;
        this.restClient = builder.build();
    }

    public boolean isConfigured() {
        return skyvernApiUrl != null && !skyvernApiUrl.isBlank()
            && skyvernApiKey != null && !skyvernApiKey.isBlank();
    }

    /**
     * Submits an auto-apply task to Skyvern for the given job posting.
     * Returns the Skyvern task ID.
     */
    public String autoApply(Long userId, Long jobId) {
        if (!isConfigured()) {
            throw new IllegalStateException("Skyvern is not configured on this server. Set SKYVERN_API_URL and SKYVERN_API_KEY.");
        }

        JobPosting job = jobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("User not found"));

        // Gather candidate info from profile
        String phone = null;
        String linkedinUrl = null;
        var profileOpt = profileRepository.findByUserId(userId);
        if (profileOpt.isPresent()) {
            var profile = profileOpt.get();
            phone = profile.getPhone();
            linkedinUrl = profile.getLinkedinUrl();
        }

        Map<String, Object> payload = buildPayload(user, job, phone, linkedinUrl);

        try {
            String url = skyvernApiUrl.stripTrailing() + "/api/v1/tasks";
            JsonNode response = restClient.post()
                .uri(url)
                .header("x-api-key", skyvernApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(payload))
                .retrieve()
                .body(JsonNode.class);

            String taskId = response != null ? response.path("task_id").asText(null) : null;
            if (taskId == null || taskId.isBlank()) {
                throw new IllegalStateException("Skyvern did not return a task ID");
            }
            log.info("Auto-apply task {} created for user {} on job {}", taskId, userId, jobId);
            return taskId;
        } catch (RestClientResponseException e) {
            log.error("Skyvern API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Skyvern API error: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to create Skyvern auto-apply task: {}", e.getMessage());
            throw new IllegalStateException("Auto-apply failed: " + e.getMessage());
        }
    }

    /** Check the status of a Skyvern task. Returns the status string. */
    public String getTaskStatus(String taskId) {
        if (!isConfigured()) return "NOT_CONFIGURED";
        try {
            String url = skyvernApiUrl.stripTrailing() + "/api/v1/tasks/" + taskId;
            JsonNode response = restClient.get()
                .uri(url)
                .header("x-api-key", skyvernApiKey)
                .retrieve()
                .body(JsonNode.class);
            return response != null ? response.path("status").asText("UNKNOWN") : "UNKNOWN";
        } catch (Exception e) {
            log.warn("Could not check Skyvern task status for {}: {}", taskId, e.getMessage());
            return "UNKNOWN";
        }
    }

    private Map<String, Object> buildPayload(User user, JobPosting job, String phone, String linkedinUrl) {
        Map<String, Object> navPayload = new HashMap<>();
        navPayload.put("full_name", user.getName() != null ? user.getName() : user.getEmail());
        navPayload.put("email", user.getEmail());
        if (phone != null && !phone.isBlank()) navPayload.put("phone", phone);
        if (linkedinUrl != null && !linkedinUrl.isBlank()) navPayload.put("linkedin_url", linkedinUrl);

        String goal = String.format(
            "Apply to the job posting for '%s' at '%s'. Use Easy Apply if available. " +
            "Fill all required fields using the provided candidate information. " +
            "Submit the application when all required fields are filled.",
            job.getTitle(), job.getCompany());

        Map<String, Object> body = new HashMap<>();
        body.put("url", job.getApplyUrl());
        body.put("navigation_goal", goal);
        body.put("data_extraction_goal", "Confirm whether the application was submitted successfully.");
        body.put("navigation_payload", navPayload);
        body.put("proxy_location", "NONE");
        return body;
    }
}
