package com.smartjobtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.model.GoogleCalendarToken;
import com.smartjobtracker.repository.GoogleCalendarTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GoogleCalendarService {
    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.events";
    private static final String EVENTS_URL_TEMPLATE = "https://www.googleapis.com/calendar/v3/calendars/%s/events";

    private final GoogleCalendarTokenRepository tokenRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${GOOGLE_CLIENT_ID:}")
    private String clientId;

    @Value("${GOOGLE_CLIENT_SECRET:}")
    private String clientSecret;

    @Value("${app.google-calendar.redirect-uri:http://localhost:8080/api/google-calendar/callback}")
    private String redirectUri;

    public GoogleCalendarService(GoogleCalendarTokenRepository tokenRepository, ObjectMapper objectMapper) {
        this.tokenRepository = tokenRepository;
        this.objectMapper = objectMapper;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public boolean isConnected(Long userId) {
        return tokenRepository.findByUserId(userId).isPresent();
    }

    /** Returns the Google OAuth2 authorization URL for calendar access. */
    public String buildAuthorizationUrl(String state) {
        return "https://accounts.google.com/o/oauth2/v2/auth"
            + "?client_id=" + encode(clientId)
            + "&redirect_uri=" + encode(redirectUri)
            + "&response_type=code"
            + "&scope=" + encode(CALENDAR_SCOPE)
            + "&access_type=offline"
            + "&prompt=consent"
            + "&state=" + encode(state);
    }

    /** Exchanges an authorization code for tokens and stores them. */
    @Transactional
    public void exchangeCodeAndStore(Long userId, String code) throws Exception {
        String body = "code=" + encode(code)
            + "&client_id=" + encode(clientId)
            + "&client_secret=" + encode(clientSecret)
            + "&redirect_uri=" + encode(redirectUri)
            + "&grant_type=authorization_code";
        JsonNode json = postForm(TOKEN_ENDPOINT, body);
        storeTokens(userId, json);
    }

    /**
     * Creates a Google Calendar event at {@code eventAt} with popup and email reminders
     * at each of the given {@code offsetMinutes} values before the event.
     * Returns the created event id, or empty if the user is not connected or the call fails.
     */
    public Optional<String> createEvent(Long userId, String summary, String description,
                                         OffsetDateTime eventAt, String timezone,
                                         List<Integer> offsetMinutes) {
        GoogleCalendarToken token = loadAndRefresh(userId);
        if (token == null) return Optional.empty();
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("summary", summary);
            if (description != null && !description.isBlank()) event.put("description", description);

            Map<String, String> startMap = new LinkedHashMap<>();
            startMap.put("dateTime", eventAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            startMap.put("timeZone", timezone != null ? timezone : "UTC");
            event.put("start", startMap);

            Map<String, String> endMap = new LinkedHashMap<>();
            endMap.put("dateTime", eventAt.plusHours(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            endMap.put("timeZone", timezone != null ? timezone : "UTC");
            event.put("end", endMap);

            List<Map<String, Object>> overrides = new ArrayList<>();
            for (int mins : offsetMinutes) {
                Map<String, Object> popup = new LinkedHashMap<>();
                popup.put("method", "popup");
                popup.put("minutes", mins);
                overrides.add(popup);
                Map<String, Object> email = new LinkedHashMap<>();
                email.put("method", "email");
                email.put("minutes", mins);
                overrides.add(email);
            }
            Map<String, Object> reminders = new LinkedHashMap<>();
            reminders.put("useDefault", false);
            reminders.put("overrides", overrides);
            event.put("reminders", reminders);

            String url = String.format(EVENTS_URL_TEMPLATE,
                URLEncoder.encode(token.getCalendarId(), StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token.getAccessToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(event)))
                .build();

            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                JsonNode resp = objectMapper.readTree(response.body());
                String eventId = resp.path("id").asText(null);
                log.info("Google Calendar event created for user {}: {}", userId, eventId);
                return Optional.ofNullable(eventId);
            }
            log.warn("Calendar event creation failed (HTTP {}) for user {}: {}",
                response.statusCode(), userId, response.body());
        } catch (Exception e) {
            log.error("Failed to create Google Calendar event for user {}: {}", userId, e.getMessage());
        }
        return Optional.empty();
    }

    @Transactional
    public void disconnect(Long userId) {
        tokenRepository.deleteByUserId(userId);
    }

    private GoogleCalendarToken loadAndRefresh(Long userId) {
        Optional<GoogleCalendarToken> opt = tokenRepository.findByUserId(userId);
        if (opt.isEmpty()) return null;
        return refreshIfExpired(opt.get());
    }

    private GoogleCalendarToken refreshIfExpired(GoogleCalendarToken token) {
        if (token.getTokenExpiry() == null || token.getTokenExpiry().isAfter(OffsetDateTime.now().plusMinutes(2))) {
            return token;
        }
        if (token.getRefreshToken() == null || token.getRefreshToken().isBlank()) {
            log.warn("Google Calendar token expired for user {} — no refresh token stored", token.getUserId());
            return null;
        }
        try {
            String body = "refresh_token=" + encode(token.getRefreshToken())
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&grant_type=refresh_token";
            JsonNode json = postForm(TOKEN_ENDPOINT, body);
            token.setAccessToken(json.path("access_token").asText());
            if (json.has("refresh_token")) token.setRefreshToken(json.path("refresh_token").asText());
            token.setTokenExpiry(OffsetDateTime.now().plusSeconds(json.path("expires_in").asInt(3600)));
            token.setUpdatedAt(OffsetDateTime.now());
            return tokenRepository.save(token);
        } catch (Exception e) {
            log.error("Failed to refresh Google Calendar token for user {}: {}", token.getUserId(), e.getMessage());
            return null;
        }
    }

    private void storeTokens(Long userId, JsonNode json) {
        GoogleCalendarToken token = tokenRepository.findByUserId(userId).orElseGet(GoogleCalendarToken::new);
        token.setUserId(userId);
        token.setAccessToken(json.path("access_token").asText());
        if (json.has("refresh_token")) token.setRefreshToken(json.path("refresh_token").asText());
        token.setTokenExpiry(OffsetDateTime.now().plusSeconds(json.path("expires_in").asInt(3600)));
        token.setUpdatedAt(OffsetDateTime.now());
        tokenRepository.save(token);
    }

    private JsonNode postForm(String url, String formBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formBody))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(response.body());
        if (json.has("error")) {
            String desc = json.path("error_description").asText(json.path("error").asText("Unknown error"));
            throw new RuntimeException("Google token error: " + desc);
        }
        return json;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
