package com.smartjobtracker.controller;

import com.smartjobtracker.repository.UserRepository;
import com.smartjobtracker.service.GoogleCalendarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/google-calendar")
public class GoogleCalendarController {
    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarController.class);
    private static final long STATE_TTL_MS = 300_000; // 5 minutes

    private final GoogleCalendarService calendarService;
    private final UserRepository userRepository;
    // state token → [userId, issuedAt]
    private final ConcurrentHashMap<String, long[]> pendingStates = new ConcurrentHashMap<>();

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public GoogleCalendarController(GoogleCalendarService calendarService, UserRepository userRepository) {
        this.calendarService = calendarService;
        this.userRepository = userRepository;
    }

    /** Returns the Google OAuth URL to redirect the browser to for calendar authorization. */
    @GetMapping("/connect-url")
    public ResponseEntity<?> connectUrl(Authentication authentication) {
        if (!calendarService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Google OAuth is not configured on this server"));
        }
        Long userId = resolveUserId(authentication);
        String state = Base64.getUrlEncoder().withoutPadding()
            .encodeToString((userId + ":" + System.currentTimeMillis()).getBytes());
        pendingStates.put(state, new long[]{userId, System.currentTimeMillis()});
        evictStale();
        return ResponseEntity.ok(Map.of("url", calendarService.buildAuthorizationUrl(state)));
    }

    /** Google OAuth2 callback — browser redirect, so no JWT available here. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {

        if (error != null || code == null || state == null) {
            log.warn("Google Calendar callback received error: {}", error);
            return redirect(frontendUrl + "/reminders?calendar=error");
        }
        long[] entry = pendingStates.remove(state);
        if (entry == null || System.currentTimeMillis() - entry[1] > STATE_TTL_MS) {
            return redirect(frontendUrl + "/reminders?calendar=error&reason=state_expired");
        }
        long userId = entry[0];
        try {
            calendarService.exchangeCodeAndStore(userId, code);
            log.info("Google Calendar connected for user {}", userId);
            return redirect(frontendUrl + "/reminders?calendar=connected");
        } catch (Exception e) {
            log.error("Google Calendar code exchange failed for user {}: {}", userId, e.getMessage());
            return redirect(frontendUrl + "/reminders?calendar=error");
        }
    }

    /** Returns whether the current user has connected Google Calendar. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        return ResponseEntity.ok(Map.of(
            "connected", calendarService.isConnected(userId),
            "configured", calendarService.isConfigured()
        ));
    }

    /** Revokes the stored Google Calendar tokens for the current user. */
    @DeleteMapping("/disconnect")
    public ResponseEntity<Void> disconnect(Authentication authentication) {
        calendarService.disconnect(resolveUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    private Long resolveUserId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new IllegalArgumentException("User not found"))
            .getId();
    }

    private ResponseEntity<Void> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND).header("Location", url).build();
    }

    private void evictStale() {
        long cutoff = System.currentTimeMillis() - STATE_TTL_MS;
        pendingStates.entrySet().removeIf(e -> e.getValue()[1] < cutoff);
    }
}
