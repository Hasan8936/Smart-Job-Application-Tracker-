package com.smartjobtracker;

import com.smartjobtracker.dto.AuthRequest;
import com.smartjobtracker.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * End-to-end coverage of the Phase 1 candidate-profile flow against the real
 * HTTP stack: register → login → upload resume → extract → read → edit → read,
 * plus a protection check for the unauthenticated case.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class CandidateProfileIntegrationTest {

    @LocalServerPort
    private int port;

    private static final String RESUME_TEXT =
            "Jane Dev\n" +
            "Software Engineer\n\n" +
            "SKILLS\n" +
            "Java Python SQL Docker AWS Git Spring Boot React\n\n" +
            "EDUCATION\n" +
            "B.E. Computer Engineering, Thapar Institute, 2026\n\n" +
            "EXPERIENCE\n" +
            "Data Engineering Intern at Acme Corp\n\n" +
            "PROJECTS\n" +
            "Built a job tracker with Spring Boot and React\n";

    @Test
    public void extract_read_edit_and_protection_flow() {
        RestTemplate rest = new RestTemplate();
        String base = "http://localhost:" + port;
        String email = "profiletest@example.com";

        // register + login
        RegisterRequest reg = new RegisterRequest();
        reg.setName("Profile Test"); reg.setEmail(email); reg.setPassword("pass123");
        rest.postForEntity(base + "/api/auth/register", reg, String.class);

        AuthRequest login = new AuthRequest();
        login.setEmail(email); login.setPassword("pass123");
        ResponseEntity<String> loginResp = rest.postForEntity(base + "/api/auth/login", login, String.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = extractToken(loginResp.getBody());
        assertThat(token).isNotBlank();

        HttpHeaders auth = new HttpHeaders();
        auth.setBearerAuth(token);

        // upload a .txt resume (reuses the existing /api/resume/upload endpoint, unchanged)
        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        uploadHeaders.setBearerAuth(token);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(RESUME_TEXT.getBytes(StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "resume.txt"; }
        });
        ResponseEntity<String> upload = rest.postForEntity(base + "/api/resume/upload",
                new HttpEntity<>(form, uploadHeaders), String.class);
        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.OK);

        // extract profile from the latest resume
        ResponseEntity<String> extract = rest.exchange(base + "/api/profile/extract",
                HttpMethod.POST, new HttpEntity<>(auth), String.class);
        assertThat(extract.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(extract.getBody()).contains("Java").contains("Spring Boot").contains("Docker");

        // read it back
        ResponseEntity<String> get1 = rest.exchange(base + "/api/profile",
                HttpMethod.GET, new HttpEntity<>(auth), String.class);
        assertThat(get1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get1.getBody()).contains("Java").contains("Thapar");

        // manual edit replaces the stored data
        HttpHeaders jsonAuth = new HttpHeaders();
        jsonAuth.setContentType(MediaType.APPLICATION_JSON);
        jsonAuth.setBearerAuth(token);
        String editJson = "{\"skills\":[\"Kubernetes\"],\"programmingLanguages\":[\"Go\"],"
                + "\"frameworks\":[],\"projects\":[],\"education\":[],\"experience\":[],"
                + "\"preferredRoles\":[\"Data Engineer\"]}";
        ResponseEntity<String> put = rest.exchange(base + "/api/profile",
                HttpMethod.PUT, new HttpEntity<>(editJson, jsonAuth), String.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(put.getBody()).contains("Kubernetes").contains("Data Engineer");

        // edits persist
        ResponseEntity<String> get2 = rest.exchange(base + "/api/profile",
                HttpMethod.GET, new HttpEntity<>(auth), String.class);
        assertThat(get2.getBody()).contains("Kubernetes").contains("Go").contains("Data Engineer");

        // protected: no token is rejected (401 or 403 depending on the entry point)
        try {
            rest.exchange(base + "/api/profile", HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), String.class);
            fail("expected the profile endpoint to reject an unauthenticated request");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        }
    }

    private String extractToken(String body) {
        Matcher m = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(body == null ? "" : body);
        return m.find() ? m.group(1) : "";
    }
}
