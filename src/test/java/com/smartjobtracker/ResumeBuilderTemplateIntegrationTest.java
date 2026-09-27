package com.smartjobtracker;

import com.smartjobtracker.dto.AuthRequest;
import com.smartjobtracker.dto.RegisterRequest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Resume builder templates over the real HTTP stack: list templates, preview each as PDF,
 * export .tex, reject an unknown template, and require authentication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ResumeBuilderTemplateIntegrationTest {

    @LocalServerPort
    private int port;

    private static String body(String template) {
        String t = template == null ? "" : "\"template\":\"" + template + "\",";
        return "{" + t
                + "\"targetRole\":\"Backend Engineer\","
                + "\"personalInfo\":{\"name\":\"Aisha Khan\",\"email\":\"aisha@example.com\",\"linkedin\":\"linkedin.com/in/aisha\"},"
                + "\"experience\":[{\"company\":\"Acme\",\"role\":\"Software Engineer\",\"startDate\":\"01/2023\",\"endDate\":\"06/2024\","
                + "\"current\":false,\"bullets\":[\"Built payment APIs\"]}],"
                + "\"education\":[{\"institution\":\"VIT University\",\"degree\":\"B.Tech\",\"field\":\"CS\",\"startYear\":\"2019\",\"endYear\":\"2023\"}],"
                + "\"skills\":{\"languages\":[\"Java\"],\"frameworks\":[],\"tools\":[],\"other\":[]}"
                + "}";
    }

    @Test
    public void templates_preview_latex_validation_and_protection() throws Exception {
        RestTemplate rest = new RestTemplate();
        String base = "http://localhost:" + port;
        String email = "buildertemplates@example.com";

        RegisterRequest reg = new RegisterRequest();
        reg.setName("Builder Test"); reg.setEmail(email); reg.setPassword("pass1234");
        rest.postForEntity(base + "/api/auth/register", reg, String.class);
        AuthRequest login = new AuthRequest();
        login.setEmail(email); login.setPassword("pass1234");
        String token = extractToken(rest.postForEntity(base + "/api/auth/login", login, String.class).getBody());
        assertThat(token).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // template catalogue
        ResponseEntity<String> list = rest.exchange(base + "/api/resume/build/templates", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("\"id\":\"jakes\"", "\"id\":\"sb2nov\"", "\"id\":\"compact\"", "MIT License");

        // every template previews as a readable PDF; no template means the default
        for (String t : new String[]{"jakes", "sb2nov", "compact", null}) {
            ResponseEntity<byte[]> pdf = rest.exchange(base + "/api/resume/build/preview", HttpMethod.POST,
                    new HttpEntity<>(body(t), headers), byte[].class);
            assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(pdf.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
            try (PDDocument doc = PDDocument.load(pdf.getBody())) {
                String text = new PDFTextStripper().getText(doc);
                assertThat(text).contains("Aisha Khan", "Software Engineer", "Acme", "01/2023 \u2013 06/2024", "VIT University");
            }
        }

        // export renders the chosen template and saves the resume to the account
        ResponseEntity<byte[]> exported = rest.exchange(base + "/api/resume/build/export", HttpMethod.POST,
                new HttpEntity<>(body("compact"), headers), byte[].class);
        assertThat(exported.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exported.getHeaders().getFirst("X-Resume-Id")).isNotBlank();
        try (PDDocument doc = PDDocument.load(exported.getBody())) {
            assertThat(new PDFTextStripper().getText(doc)).contains("Software Engineer — Acme");
        }

        // .tex export follows the chosen template
        ResponseEntity<byte[]> tex = rest.exchange(base + "/api/resume/build/export-latex", HttpMethod.POST,
                new HttpEntity<>(body("sb2nov"), headers), byte[].class);
        assertThat(tex.getStatusCode()).isEqualTo(HttpStatus.OK);
        String latex = new String(tex.getBody(), StandardCharsets.UTF_8);
        assertThat(latex).contains("sb2nov/resume", "\\resumeSubheading", "\\href{https://linkedin.com/in/aisha}");

        // unknown template -> 400 with a field error
        try {
            rest.exchange(base + "/api/resume/build/preview", HttpMethod.POST,
                    new HttpEntity<>(body("two-column-fancy"), headers), String.class);
            fail("expected 400 for an unknown template");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getResponseBodyAsString()).contains("template");
        }

        // unauthenticated -> rejected
        HttpHeaders anon = new HttpHeaders();
        anon.setContentType(MediaType.APPLICATION_JSON);
        try {
            rest.exchange(base + "/api/resume/build/preview", HttpMethod.POST, new HttpEntity<>(body("jakes"), anon), byte[].class);
            fail("expected unauthenticated request to be rejected");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isIn(401, 403);
        }
    }

    private String extractToken(String body) {
        Matcher m = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(body == null ? "" : body);
        return m.find() ? m.group(1) : "";
    }
}
