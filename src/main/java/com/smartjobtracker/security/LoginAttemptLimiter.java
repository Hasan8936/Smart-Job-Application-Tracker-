package com.smartjobtracker.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles password guessing: after {@code max} failed logins for one email inside {@code window}, further
 * attempts for that email are refused until the window passes. In-memory — fine for the single Render
 * instance; a multi-instance deployment would need a shared store.
 */
@Component
public class LoginAttemptLimiter {

    private record Window(long startedAtMillis, int failures) {}

    private final ConcurrentHashMap<String, Window> failures = new ConcurrentHashMap<>();
    private final int max;
    private final long windowMillis;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public LoginAttemptLimiter(@Value("${app.auth.max-login-failures:10}") int max,
                               @Value("${app.auth.login-failure-window:PT15M}") Duration window) {
        this(max, window, Clock.systemUTC());
    }

    LoginAttemptLimiter(int max, Duration window, Clock clock) {
        this.max = max;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    public boolean isBlocked(String email) {
        Window w = failures.get(key(email));
        if (w == null) return false;
        if (expired(w)) {
            failures.remove(key(email), w);
            return false;
        }
        return w.failures() >= max;
    }

    public void recordFailure(String email) {
        long now = clock.millis();
        failures.compute(key(email), (k, w) -> (w == null || expired(w)) ? new Window(now, 1) : new Window(w.startedAtMillis(), w.failures() + 1));
        if (failures.size() > 50_000) failures.values().removeIf(this::expired); // bound memory under a spray
    }

    public void recordSuccess(String email) {
        failures.remove(key(email));
    }

    private boolean expired(Window w) {
        return clock.millis() - w.startedAtMillis() > windowMillis;
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
