package com.smartjobtracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Security regressions for registration, login and admin promotion. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.super-admin-email=boss-hardening@example.com", "app.auth.max-login-failures=3"})
@ActiveProfiles("test")
public class AuthHardeningIntegrationTest {

    @LocalServerPort
    private int port;

    private final RestTemplate rest = new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory());
    private final ObjectMapper mapper = new ObjectMapper();

    private String base() { return "http://localhost:" + port + "/api"; }

    private ResponseEntity<String> register(String email, String password) {
        return rest.postForEntity(base() + "/auth/register",
                Map.of("name", "T", "email", email, "password", password), String.class);
    }

    private String login(String email, String password) throws Exception {
        ResponseEntity<String> r = rest.postForEntity(base() + "/auth/login",
                Map.of("email", email, "password", password), String.class);
        JsonNode body = mapper.readTree(r.getBody());
        return body.get("token").asText();
    }

    @Test
    public void caseVariantOfSuperAdminEmailCannotRegisterOrBecomeAdmin() throws Exception {
        assertThat(register("boss-hardening@example.com", "realAdminPass1").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Attacker registers the same address with different letter case.
        assertThatThrownBy(() -> register("BOSS-hardening@example.com", "attackerPass1"))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);

        // And even if they could log in with it, the admin API must stay closed.
        String realToken = login("boss-hardening@example.com", "realAdminPass1");
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(realToken);
        ResponseEntity<String> admin = rest.exchange(base() + "/admin/users", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void registerRejectsMissingFieldsWithBadRequest() {
        assertThatThrownBy(() -> rest.postForEntity(base() + "/auth/register",
                Map.of("name", "T", "email", "not-an-email"), String.class))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    public void repeatedFailedLoginsAreThrottled() {
        assertThat(register("throttle@example.com", "rightPass1").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> login("throttle@example.com", "wrong"))
                    .isInstanceOf(HttpClientErrorException.Unauthorized.class);
        }
        // Even the correct password is refused while the window is locked.
        assertThatThrownBy(() -> login("throttle@example.com", "rightPass1"))
                .isInstanceOf(HttpClientErrorException.TooManyRequests.class);
    }
}
