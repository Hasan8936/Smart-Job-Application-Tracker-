package com.smartjobtracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.AuthRequest;
import com.smartjobtracker.dto.RegisterRequest;
import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.model.JobSkill;
import com.smartjobtracker.repository.JobPostingRepository;
import com.smartjobtracker.repository.JobSkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Universal resume end to end: create → match a job → edit (add a skill) → the match reflects the edit,
 * the uploaded resume is untouched, and matching defaults to the universal resume.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class UniversalResumeIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private JobPostingRepository jobs;
    @Autowired private JobSkillRepository skills;

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String UPLOADED_TEXT = "Jane Dev\nSKILLS\nJava Python SQL\nEXPERIENCE\nIntern at Acme 2024\n";

    private String universal(String template, String... tools) {
        return "{\"template\":\"" + template + "\",\"targetRole\":\"Backend Engineer\","
                + "\"personalInfo\":{\"name\":\"Jane Dev\",\"email\":\"jane@example.com\"},"
                + "\"experience\":[{\"company\":\"Acme\",\"role\":\"Backend Intern\",\"startDate\":\"Jan 2024\",\"endDate\":\"Jun 2024\","
                + "\"current\":false,\"bullets\":[\"Built REST APIs in Java\"]}],"
                + "\"education\":[{\"institution\":\"VIT University\",\"degree\":\"B.Tech\",\"field\":\"CS\"}],"
                + "\"skills\":{\"languages\":[\"Java\"],\"frameworks\":[],\"tools\":" + json(tools) + ",\"other\":[]}}";
    }

    private String json(String[] values) {
        try { return mapper.writeValueAsString(values); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private HttpHeaders login(String base, String email) {
        RegisterRequest reg = new RegisterRequest();
        reg.setName("Universal Test"); reg.setEmail(email); reg.setPassword("pass1234");
        rest.postForEntity(base + "/api/auth/register", reg, String.class);
        AuthRequest login = new AuthRequest();
        login.setEmail(email); login.setPassword("pass1234");
        JsonNode body = read(rest.postForEntity(base + "/api/auth/login", login, String.class).getBody());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(body.path("token").asText());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private JsonNode read(String body) {
        try { return mapper.readTree(body); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private JsonNode call(String url, HttpMethod method, String body, HttpHeaders headers) {
        return read(rest.exchange(url, method, new HttpEntity<>(body, headers), String.class).getBody());
    }

    private long uploadResume(String base, HttpHeaders auth) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(auth.getFirst(HttpHeaders.AUTHORIZATION).substring("Bearer ".length()));
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(UPLOADED_TEXT.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "resume.txt"; }
        });
        return read(rest.postForEntity(base + "/api/resume/upload", new HttpEntity<>(form, h), String.class).getBody()).path("id").asLong();
    }

    private long jobRequiring(String... required) {
        JobPosting p = new JobPosting();
        p.setProvider("official"); p.setExternalId("universal-test-" + System.nanoTime()); p.setDedupeHash("h" + System.nanoTime());
        p.setCompany("Acme"); p.setTitle("Backend Engineer"); p.setApplyUrl("https://example.test/job");
        p.setDescription("Backend Engineer. 2 years experience. Required: Java and Kubernetes.");
        long id = jobs.save(p).getId();
        List<JobSkill> rows = new ArrayList<>();
        for (String name : required) {
            JobSkill s = new JobSkill();
            s.setJobPostingId(id); s.setName(name); s.setNormalizedName(name.toLowerCase()); s.setRequirement("REQUIRED");
            rows.add(s);
        }
        skills.saveAll(rows);
        return id;
    }

    @Test
    public void createMatchEditRematchAndUploadIsUntouched() {
        String base = "http://localhost:" + port;
        HttpHeaders auth = login(base, "universal@example.com");
        long uploadedId = uploadResume(base, auth);
        String uploadedTextBefore = uploadedText(base, auth, uploadedId);
        long jobId = jobRequiring("Java", "Kubernetes");

        // before any universal resume, matching defaults to the upload
        JsonNode before = call(base + "/api/resume/matching", HttpMethod.GET, null, auth);
        assertThat(before.path("source").asText()).isEqualTo("LATEST_UPLOAD");
        assertThat(before.path("resumeId").asLong()).isEqualTo(uploadedId);

        // create
        JsonNode created = call(base + "/api/resume/universal", HttpMethod.PUT, universal("sb2nov"), auth);
        long universalResumeId = created.path("resumeId").asLong();
        assertThat(universalResumeId).isNotEqualTo(uploadedId);
        assertThat(created.path("template").asText()).isEqualTo("sb2nov");
        assertThat(created.path("usedForMatching").asBoolean()).isTrue();

        JsonNode matching = call(base + "/api/resume/matching", HttpMethod.GET, null, auth);
        assertThat(matching.path("source").asText()).isEqualTo("UNIVERSAL");
        assertThat(matching.path("resumeId").asLong()).isEqualTo(universalResumeId);

        // match
        String matchBody = "{\"resumeId\":" + universalResumeId + ",\"jobId\":" + jobId + "}";
        JsonNode first = call(base + "/api/match/hybrid-score", HttpMethod.POST, matchBody, auth);
        assertThat(first.path("missingRequiredSkills").toString()).contains("Kubernetes");
        double firstScore = first.path("overallMatch").asDouble();

        // edit: add a skill → same resume id, and the next match reflects it
        JsonNode edited = call(base + "/api/resume/universal", HttpMethod.PUT, universal("sb2nov", "Kubernetes"), auth);
        assertThat(edited.path("resumeId").asLong()).isEqualTo(universalResumeId);
        JsonNode second = call(base + "/api/match/hybrid-score", HttpMethod.POST, matchBody, auth);
        assertThat(second.path("missingRequiredSkills").toString()).doesNotContain("Kubernetes");
        assertThat(second.path("strongMatches").toString()).contains("Kubernetes");
        assertThat(second.path("overallMatch").asDouble()).isGreaterThan(firstScore);
        assertThat(second.path("breakdown").path("matchedRequiredCount").asInt())
                .isGreaterThan(first.path("breakdown").path("matchedRequiredCount").asInt());

        // GET returns the saved structured data
        JsonNode got = call(base + "/api/resume/universal", HttpMethod.GET, null, auth);
        assertThat(got.path("resume").path("skills").path("tools").toString()).contains("Kubernetes");

        // the uploaded resume is untouched
        assertThat(uploadedText(base, auth, uploadedId)).isNotBlank().isEqualTo(uploadedTextBefore);
    }

    /** Job cards score against the universal resume even with no candidate profile; the resume is named after the user. */
    @Test
    public void jobBoardScoresWithUniversalResumeAndResumeIsNamedAfterUser() {
        String base = "http://localhost:" + port;
        HttpHeaders auth = login(base, "universal-board@example.com");
        long jobId = jobRequiring("Java", "Kubernetes");

        JsonNode created = call(base + "/api/resume/universal", HttpMethod.PUT, universal("jakes"), auth);
        long universalResumeId = created.path("resumeId").asLong();
        JsonNode matching = call(base + "/api/resume/matching", HttpMethod.GET, null, auth);
        assertThat(matching.path("fileName").asText()).isEqualTo("Jane Dev");

        Integer score = null;
        for (JsonNode job : call(base + "/api/jobs?size=100", HttpMethod.GET, null, auth).path("content"))
            if (job.path("id").asLong() == jobId) score = job.path("matchScore").isNull() ? null : job.path("matchScore").asInt();
        assertThat(score).as("Java of Java+Kubernetes matched from the universal resume").isEqualTo(50);

        // a rename on the resume renames the linked matching resume, keeping its id
        JsonNode renamed = call(base + "/api/resume/universal", HttpMethod.PUT,
                universal("jakes").replace("\"name\":\"Jane Dev\"", "\"name\":\"Jane Q Dev\""), auth);
        assertThat(renamed.path("resumeId").asLong()).isEqualTo(universalResumeId);
        assertThat(call(base + "/api/resume/matching", HttpMethod.GET, null, auth).path("fileName").asText()).isEqualTo("Jane Q Dev");
    }

    private String uploadedText(String base, HttpHeaders auth, long resumeId) {
        for (JsonNode r : call(base + "/api/resume/me", HttpMethod.GET, null, auth))
            if (r.path("id").asLong() == resumeId) return r.path("extractedText").asText();
        throw new AssertionError("uploaded resume " + resumeId + " missing");
    }

    @Test
    public void validationOwnershipAndOverwriteRules() {
        String base = "http://localhost:" + port;
        HttpHeaders owner = login(base, "universal-owner@example.com");
        HttpHeaders other = login(base, "universal-other@example.com");
        long ownersUpload = uploadResume(base, owner);

        expectStatus(() -> call(base + "/api/resume/universal", HttpMethod.GET, null, other), 404);
        expectStatus(() -> call(base + "/api/resume/universal", HttpMethod.PUT, universal("two-column"), owner), 400);
        // someone else's resume can't be imported
        expectStatus(() -> call(base + "/api/resume/universal/from-resume?resumeId=" + ownersUpload, HttpMethod.POST, null, other), 404);

        JsonNode fromUpload = call(base + "/api/resume/universal/from-resume?resumeId=" + ownersUpload, HttpMethod.POST, null, owner);
        assertThat(fromUpload.path("resume").path("personalInfo").path("email").asText()).isEqualTo("universal-owner@example.com");
        assertThat(fromUpload.path("resumeId").asLong()).isNotEqualTo(ownersUpload);
        expectStatus(() -> call(base + "/api/resume/universal/from-resume?resumeId=" + ownersUpload, HttpMethod.POST, null, owner), 409);
        JsonNode replaced = call(base + "/api/resume/universal/from-resume?resumeId=" + ownersUpload + "&overwrite=true", HttpMethod.POST, null, owner);
        assertThat(replaced.path("resumeId").asLong()).isEqualTo(fromUpload.path("resumeId").asLong());

        expectStatus(() -> rest.exchange(base + "/api/resume/universal", HttpMethod.GET, new HttpEntity<>(null, new HttpHeaders()), String.class), 401, 403);
    }

    private void expectStatus(Runnable call, int... allowed) {
        try {
            call.run();
            fail("expected HTTP " + java.util.Arrays.toString(allowed));
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isIn(java.util.Arrays.stream(allowed).boxed().toArray());
        }
    }
}
