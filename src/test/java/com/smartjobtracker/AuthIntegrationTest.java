package com.smartjobtracker;

import com.smartjobtracker.dto.AuthRequest;
import com.smartjobtracker.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class AuthIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    public void registerAndLogin() {
        RestTemplate rest = new RestTemplate();
        String base = "http://localhost:" + port + "/api/auth";

        RegisterRequest r = new RegisterRequest();
        r.setName("ITest"); r.setEmail("itest@example.com"); r.setPassword("pass123");
        ResponseEntity<String> reg = rest.postForEntity(base + "/register", r, String.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        AuthRequest a = new AuthRequest();
        a.setEmail("itest@example.com"); a.setPassword("pass123");
        ResponseEntity<String> login = rest.postForEntity(base + "/login", a, String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).contains("token");
    }
}
