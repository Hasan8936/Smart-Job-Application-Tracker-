package com.smartjobtracker;

import com.smartjobtracker.dto.AuthRequest;
import com.smartjobtracker.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class ApplicationIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    public void createAndListApplications() {
        RestTemplate rest = new RestTemplate();
        String baseAuth = "http://localhost:" + port + "/api/auth";
        RegisterRequest r = new RegisterRequest();
        r.setName("AppTest"); r.setEmail("apptest@example.com"); r.setPassword("pass123");
        rest.postForEntity(baseAuth + "/register", r, String.class);

        AuthRequest a = new AuthRequest();
        a.setEmail("apptest@example.com"); a.setPassword("pass123");
        ResponseEntity<String> login = rest.postForEntity(baseAuth + "/login", a, String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = login.getBody().replaceAll(".*\"token\"\s*:\s*\"([^\"]+)\".*", "$1");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        String createJson = "{\"companyName\":\"Acme\",\"roleTitle\":\"SDE\",\"jobDescription\":\"Backend\",\"status\":\"APPLIED\"}";
        HttpEntity<String> req = new HttpEntity<>(createJson, headers);
        ResponseEntity<String> created = rest.postForEntity("http://localhost:"+port+"/api/applications", req, String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        HttpEntity<Void> listReq = new HttpEntity<>(headers);
        ResponseEntity<String> list = rest.exchange("http://localhost:"+port+"/api/applications", HttpMethod.GET, listReq, String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("Acme");
    }
}
