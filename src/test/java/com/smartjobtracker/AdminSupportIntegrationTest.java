package com.smartjobtracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.dto.AuthRequest;
import com.smartjobtracker.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static com.smartjobtracker.AccountIntegrationTest.expectStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Admin console + support: non-admins get 403, SUPER_ADMIN_EMAIL promotes on login, flags appear in history,
 * suspension blocks login and API calls, ticket reply round-trip, audit rows, ticket rate limit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.super-admin-email=super-admin-it@example.com", "app.support.max-tickets-per-hour=3"})
@ActiveProfiles("test")
public class AdminSupportIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;

    // JDK client: the default HttpURLConnection-based factory cannot send PATCH.
    private final RestTemplate rest = new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory());
    private final ObjectMapper mapper = new ObjectMapper();

    private String base() { return "http://localhost:" + port; }

    private JsonNode read(String body) {
        try { return mapper.readTree(body); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private String register(String email) {
        RegisterRequest reg = new RegisterRequest();
        reg.setName(email.substring(0, email.indexOf('@'))); reg.setEmail(email); reg.setPassword("pass1234");
        rest.postForEntity(base() + "/api/auth/register", reg, String.class);
        return login(email);
    }

    private String login(String email) {
        AuthRequest login = new AuthRequest();
        login.setEmail(email); login.setPassword("pass1234");
        return read(rest.postForEntity(base() + "/api/auth/login", login, String.class).getBody()).path("token").asText();
    }

    private JsonNode call(String path, HttpMethod method, Object body, String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        String response = rest.exchange(base() + path, method, new HttpEntity<>(body, h), String.class).getBody();
        return response == null ? null : read(response);
    }

    @Test
    public void adminConsoleSupportAndSuspension() {
        String user = register("support-user@example.com");
        long userId = call("/api/users/me", HttpMethod.GET, null, user).path("id").asLong();
        assertThat(call("/api/users/me", HttpMethod.GET, null, user).path("role").asText()).isEqualTo("USER");

        // non-admin → 403 on every admin endpoint
        expectStatus(() -> call("/api/admin/users", HttpMethod.GET, null, user), 403);
        expectStatus(() -> call("/api/admin/audit", HttpMethod.GET, null, user), 403);

        // the configured super admin is promoted on login (account created after startup)
        String admin = register("super-admin-it@example.com");
        long adminId = call("/api/users/me", HttpMethod.GET, null, admin).path("id").asLong();
        assertThat(call("/api/users/me", HttpMethod.GET, null, admin).path("role").asText()).isEqualTo("ADMIN");

        // search + detail (no secrets)
        JsonNode page = call("/api/admin/users?q=support-user", HttpMethod.GET, null, admin);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(userId);
        String detailRaw = call("/api/admin/users/" + userId, HttpMethod.GET, null, admin).toString();
        assertThat(detailRaw).doesNotContainIgnoringCase("passwordHash").doesNotContain("$2a$").doesNotContainIgnoringCase("token");

        // flag → appears in history → resolve
        JsonNode flag = call("/api/admin/users/" + userId + "/flags", HttpMethod.POST,
                "{\"category\":\"ABUSE\",\"severity\":\"HIGH\",\"note\":\"Spam applications\"}", admin);
        JsonNode detail = call("/api/admin/users/" + userId, HttpMethod.GET, null, admin);
        assertThat(detail.path("flags").get(0).path("id").asLong()).isEqualTo(flag.path("id").asLong());
        assertThat(detail.path("flags").get(0).path("status").asText()).isEqualTo("OPEN");
        assertThat(call("/api/admin/flags/" + flag.path("id").asLong() + "/resolve", HttpMethod.POST, null, admin)
                .path("status").asText()).isEqualTo("RESOLVED");
        expectStatus(() -> call("/api/admin/users/" + userId + "/flags", HttpMethod.POST, "{\"category\":\"NOPE\",\"severity\":\"HIGH\"}", admin), 400);

        // support ticket: user opens, admin replies, user sees the reply
        JsonNode ticket = call("/api/support/tickets", HttpMethod.POST,
                "{\"subject\":\"Can't upload resume\",\"description\":\"The upload spinner never stops\",\"category\":\"RESUME\"}", user);
        long ticketId = ticket.path("id").asLong();
        assertThat(call("/api/admin/tickets?status=OPEN", HttpMethod.GET, null, admin).path("content").toString()).contains("Can't upload resume");
        JsonNode replied = call("/api/admin/tickets/" + ticketId + "/messages", HttpMethod.POST, "{\"body\":\"Fixed, please retry.\"}", admin);
        assertThat(replied.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(replied.path("assignedAdminId").asLong()).isEqualTo(adminId);
        JsonNode seen = call("/api/support/tickets/" + ticketId, HttpMethod.GET, null, user);
        assertThat(seen.path("messages").size()).isEqualTo(2);
        assertThat(seen.path("messages").get(1).path("fromAdmin").asBoolean()).isTrue();
        assertThat(seen.path("messages").get(1).path("body").asText()).isEqualTo("Fixed, please retry.");
        assertThat(seen.path("userEmail").isNull()).isTrue();
        assertThat(call("/api/admin/tickets/" + ticketId, HttpMethod.PATCH, "{\"status\":\"RESOLVED\"}", admin).path("status").asText())
                .isEqualTo("RESOLVED");
        // another user can't read it
        String other = register("support-other@example.com");
        expectStatus(() -> call("/api/support/tickets/" + ticketId, HttpMethod.GET, null, other), 404);

        // audited resume view is the only way to resume text
        expectStatus(() -> call("/api/admin/users/" + userId + "/resumes/999999/view", HttpMethod.POST, null, admin), 404);

        // suspension blocks the old token and login, with a clear message; admins can't be suspended
        call("/api/admin/users/" + userId + "/suspend", HttpMethod.POST, "{\"reason\":\"Spam\"}", admin);
        try {
            call("/api/users/me", HttpMethod.GET, null, user);
            fail("suspended user should be blocked");
        } catch (HttpClientErrorException e) {
            assertThat(e.getStatusCode().value()).isEqualTo(403);
            assertThat(e.getResponseBodyAsString()).contains("account_suspended");
        }
        expectStatus(() -> login("support-user@example.com"), 403);
        expectStatus(() -> call("/api/admin/users/" + adminId + "/suspend", HttpMethod.POST, "{}", admin), 409);
        call("/api/admin/users/" + userId + "/unsuspend", HttpMethod.POST, null, admin);
        assertThat(login("support-user@example.com")).isNotBlank();

        // audit rows
        for (String action : new String[]{"VIEW_USER", "FLAG_ADD", "FLAG_RESOLVE", "TICKET_REPLY", "TICKET_STATUS", "SUSPEND", "UNSUSPEND"}) {
            assertThat(jdbc.queryForObject("select count(*) from admin_audit_log where action = ? and admin_id = ?", Long.class, action, adminId))
                    .as(action).isPositive();
        }
        assertThat(call("/api/admin/audit", HttpMethod.GET, null, admin).path("totalElements").asLong()).isGreaterThanOrEqualTo(7);
    }

    @Test
    public void ticketCreationIsRateLimited() {
        String user = register("support-ratelimit@example.com");
        for (int i = 0; i < 3; i++) {
            call("/api/support/tickets", HttpMethod.POST, "{\"subject\":\"Q" + i + "\",\"description\":\"d\",\"category\":\"OTHER\"}", user);
        }
        expectStatus(() -> call("/api/support/tickets", HttpMethod.POST, "{\"subject\":\"Q4\",\"description\":\"d\",\"category\":\"OTHER\"}", user), 429);
        expectStatus(() -> call("/api/support/tickets", HttpMethod.POST, "{\"subject\":\"\",\"description\":\"d\",\"category\":\"OTHER\"}", user), 400, 429);
    }
}
