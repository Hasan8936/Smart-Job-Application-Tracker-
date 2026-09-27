package com.smartjobtracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;

/** Best-effort revocation of Google OAuth tokens (used when an account is deleted). Never logs token values. */
@Component
public class GoogleTokenRevoker {
    private static final Logger log = LoggerFactory.getLogger(GoogleTokenRevoker.class);

    private final RestClient http;
    private final String revokeUrl;

    public GoogleTokenRevoker(@Value("${app.google.revoke-url:https://oauth2.googleapis.com/revoke}") String revokeUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(5_000);
        this.http = RestClient.builder().requestFactory(factory).build();
        this.revokeUrl = revokeUrl;
    }

    public void revokeAll(Long userId, List<String> tokens) {
        int revoked = 0;
        for (String token : tokens) {
            if (token == null || token.isBlank()) continue;
            try {
                LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("token", token);
                http.post().uri(revokeUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
                revoked++;
            } catch (RuntimeException e) {
                // Already-expired or already-revoked tokens return 400; the stored copies are deleted either way.
                log.info("Google token revoke not confirmed for userId={} ({})", userId, e.getClass().getSimpleName());
            }
        }
        if (revoked > 0) log.info("Revoked {} Google token(s) for deleted userId={}", revoked, userId);
    }
}
