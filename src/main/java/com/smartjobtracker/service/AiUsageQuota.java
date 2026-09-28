package com.smartjobtracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Daily AI allowance per user, so one person can't spend the shared Gemini key's quota for everyone else.
 * One unit = one Gemini generation call (interview prep spends one per batch). Resets at 00:00 UTC.
 *
 * <p>Over budget it throws {@link GeminiApiException} (429). Features with an offline fallback (interview prep,
 * tailoring, job documents) catch it like any Gemini failure and degrade; the others show the message.
 * In-memory: fine for the single Render instance; a multi-instance deployment would need a shared counter.
 */
@Component
public class AiUsageQuota {
    private static final Logger log = LoggerFactory.getLogger(AiUsageQuota.class);

    private final int dailyUnits;
    private final Clock clock;
    private final ConcurrentHashMap<Long, int[]> used = new ConcurrentHashMap<>();   // [epochDay, units]

    @Autowired
    public AiUsageQuota(@Value("${app.gemini.user-daily-units:40}") int dailyUnits) {
        this(dailyUnits, Clock.systemUTC());
    }

    AiUsageQuota(int dailyUnits, Clock clock) {
        this.dailyUnits = dailyUnits;
        this.clock = clock;
    }

    /** Reserves {@code units} for today or throws; a non-positive limit disables the quota. */
    public void consume(Long userId, int units) {
        if (dailyUnits <= 0 || userId == null || units <= 0) return;
        long today = LocalDate.now(clock.withZone(ZoneOffset.UTC)).toEpochDay();
        boolean[] allowed = {false};
        used.compute(userId, (id, entry) -> {
            int[] e = (entry == null || entry[0] != today) ? new int[]{(int) today, 0} : entry;
            if (e[1] + units <= dailyUnits) { e[1] += units; allowed[0] = true; }
            return e;
        });
        if (!allowed[0]) {
            log.info("AI daily allowance reached for userId={} (limit {})", userId, dailyUnits);
            throw new GeminiApiException("You've used today's AI allowance. It resets at midnight UTC; offline features still work.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    /** Units left today (for tests and diagnostics). */
    public int remaining(Long userId) {
        long today = LocalDate.now(clock.withZone(ZoneOffset.UTC)).toEpochDay();
        int[] e = used.get(userId);
        return dailyUnits - (e == null || e[0] != today ? 0 : e[1]);
    }
}
