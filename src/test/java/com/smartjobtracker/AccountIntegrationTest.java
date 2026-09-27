package com.smartjobtracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.AuthRequest;
import com.smartjobtracker.dto.RegisterRequest;
import com.smartjobtracker.service.ProfilePhotoProcessorTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/** register → update details → upload photo → fetch photo → export → delete account → login fails → owned rows gone. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class AccountIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;

    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    private String base() { return "http://localhost:" + port; }

    private JsonNode read(String body) {
        try { return mapper.readTree(body); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private String register(String email, String password) {
        RegisterRequest reg = new RegisterRequest();
        reg.setName("Account Test"); reg.setEmail(email); reg.setPassword(password);
        rest.postForEntity(base() + "/api/auth/register", reg, String.class);
        return login(email, password);
    }

    private String login(String email, String password) {
        AuthRequest login = new AuthRequest();
        login.setEmail(email); login.setPassword(password);
        return read(rest.postForEntity(base() + "/api/auth/login", login, String.class).getBody()).path("token").asText();
    }

    private HttpHeaders json(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<String> call(String path, HttpMethod method, Object body, String token) {
        return rest.exchange(base() + path, method, new HttpEntity<>(body, json(token)), String.class);
    }

    @Test
    public void fullAccountLifecycle() throws Exception {
        String email = "account-lifecycle@example.com";
        String token = register(email, "pass1234");
        long userId = read(call("/api/users/me", HttpMethod.GET, null, token).getBody()).path("id").asLong();

        // details
        JsonNode updated = read(call("/api/users/me/details", HttpMethod.PUT,
                "{\"name\":\"Aisha Khan\",\"headline\":\"Backend engineer\",\"location\":\"Bengaluru\",\"phone\":\"+91 98765 43210\","
                        + "\"linkedinUrl\":\"linkedin.com/in/aisha\",\"githubUrl\":\"https://github.com/aisha\",\"websiteUrl\":\"\"}", token).getBody());
        assertThat(updated.path("name").asText()).isEqualTo("Aisha Khan");
        assertThat(updated.path("linkedinUrl").asText()).isEqualTo("https://linkedin.com/in/aisha");
        assertThat(updated.path("email").asText()).isEqualTo(email);
        expectStatus(() -> call("/api/users/me/details", HttpMethod.PUT, "{\"linkedinUrl\":\"not a url at all\"}", token), 400);

        // photo
        HttpHeaders multipart = new HttpHeaders();
        multipart.setBearerAuth(token);
        multipart.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(ProfilePhotoProcessorTest.image(900, 900, "jpg", false)) {
            @Override public String getFilename() { return "me.jpg"; }
        });
        assertThat(rest.postForEntity(base() + "/api/users/me/photo", new HttpEntity<>(form, multipart), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<byte[]> photo = rest.exchange(base() + "/api/users/me/photo", HttpMethod.GET, new HttpEntity<>(json(token)), byte[].class);
        assertThat(photo.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(photo.getHeaders().getCacheControl()).contains("private");
        assertThat(photo.getBody()).isNotEmpty();
        assertThat(read(call("/api/users/me", HttpMethod.GET, null, token).getBody()).path("hasPhoto").asBoolean()).isTrue();

        MultiValueMap<String, Object> fake = new LinkedMultiValueMap<>();
        fake.add("file", new ByteArrayResource("GIF89a-not-allowed".getBytes()) { @Override public String getFilename() { return "x.jpg"; } });
        expectStatus(() -> rest.postForEntity(base() + "/api/users/me/photo", new HttpEntity<>(fake, multipart), String.class), 415);

        // some owned data
        call("/api/applications", HttpMethod.POST, "{\"companyName\":\"Acme\",\"roleTitle\":\"Engineer\",\"status\":\"APPLIED\"}", token);
        call("/api/support/tickets", HttpMethod.POST, "{\"subject\":\"Help\",\"description\":\"Something broke\",\"category\":\"BUG\"}", token);

        // password change: wrong current password refused, right one accepted
        expectStatus(() -> call("/api/users/me/password", HttpMethod.POST, "{\"currentPassword\":\"wrong\",\"newPassword\":\"newpass123\"}", token), 400);
        expectStatus(() -> call("/api/users/me/password", HttpMethod.POST, "{\"currentPassword\":\"pass1234\",\"newPassword\":\"short\"}", token), 400);
        assertThat(call("/api/users/me/password", HttpMethod.POST, "{\"currentPassword\":\"pass1234\",\"newPassword\":\"newpass123\"}", token)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // export
        ResponseEntity<String> export = call("/api/users/me/export", HttpMethod.GET, null, token);
        assertThat(export.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");
        JsonNode data = read(export.getBody());
        assertThat(data.path("account").path("email").asText()).isEqualTo(email);
        assertThat(data.path("profileDetails").path("headline").asText()).isEqualTo("Backend engineer");
        assertThat(data.path("applications").size()).isEqualTo(1);
        assertThat(data.path("supportTickets").size()).isEqualTo(1);
        assertThat(export.getBody()).doesNotContainIgnoringCase("passwordHash").doesNotContainIgnoringCase("refresh_token")
                .doesNotContain("$2a$");

        // delete: wrong password refused, right one deletes
        expectStatus(() -> call("/api/users/me", HttpMethod.DELETE, "{\"password\":\"pass1234\"}", token), 403);
        assertThat(call("/api/users/me", HttpMethod.DELETE, "{\"password\":\"newpass123\"}", token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        // login fails, old token no longer works
        expectStatus(() -> login(email, "newpass123"), 401);
        expectStatus(() -> call("/api/users/me/details", HttpMethod.GET, null, token), 401, 403);

        // every owned row is gone
        for (String table : List.of("users where id = ?", "candidate_profiles where user_id = ?", "user_photos where user_id = ?",
                "applications where user_id = ?", "support_tickets where user_id = ?")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table, Long.class, userId)).as(table).isZero();
        }
    }

    static void expectStatus(Runnable call, int... allowed) {
        try {
            call.run();
            fail("expected HTTP " + java.util.Arrays.toString(allowed));
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isIn(java.util.Arrays.stream(allowed).boxed().toArray());
        }
    }
}
