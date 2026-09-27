package com.smartjobtracker.jobs.provider;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

/**
 * A salary range exactly as a job source or posting states it. {@code period} is one of
 * YEAR, MONTH, WEEK, DAY, HOUR (null when the source does not say). Values are whole currency units.
 */
public record SalaryInfo(Integer min, Integer max, String currency, String period) {

    public static final SalaryInfo NONE = new SalaryInfo(null, null, null, null);

    public boolean present() { return min != null || max != null; }

    /** Orders min/max and drops non-positive values; returns NONE when nothing usable is left. */
    public static SalaryInfo of(Number min, Number max, String currency, String period) {
        Integer lo = positive(min), hi = positive(max);
        if (lo == null && hi == null) return NONE;
        if (lo != null && hi != null && lo > hi) { Integer t = lo; lo = hi; hi = t; }
        String cur = currency == null || currency.isBlank() ? null : currency.trim().toUpperCase(Locale.ROOT);
        return new SalaryInfo(lo, hi, cur, period);
    }

    /**
     * Greenhouse {@code pay_input_ranges} (requested with {@code pay_transparency=true}): amounts in cents,
     * possibly one range per pay zone. Reports the span across zones in the first currency listed.
     * Greenhouse gives no pay period, so ranges under 1,000/unit are treated as hourly wages.
     */
    public static SalaryInfo fromGreenhouse(JsonNode payRanges) {
        if (payRanges == null || !payRanges.isArray() || payRanges.isEmpty()) return NONE;
        String currency = null; Long lo = null, hi = null;
        for (JsonNode r : payRanges) {
            String cur = r.path("currency_type").asText(null);
            if (currency == null) currency = cur;
            else if (cur != null && !cur.equalsIgnoreCase(currency)) continue;
            long min = r.path("min_cents").asLong(0) / 100, max = r.path("max_cents").asLong(0) / 100;
            if (min > 0) lo = lo == null ? min : Math.min(lo, min);
            if (max > 0) hi = hi == null ? max : Math.max(hi, max);
        }
        long top = hi != null ? hi : (lo != null ? lo : 0);
        if (top == 0) return NONE;
        return of(lo, hi, currency, top < 1000 ? "HOUR" : "YEAR");
    }

    /** Lever {@code salaryRange}: {@code {min, max, currency, interval: "per-year-salary" | "per-hour-wage" | ...}}. */
    public static SalaryInfo fromLever(JsonNode salaryRange) {
        if (salaryRange == null || salaryRange.isMissingNode() || salaryRange.isNull()) return NONE;
        String interval = salaryRange.path("interval").asText("").toLowerCase(Locale.ROOT);
        String period = interval.contains("year") ? "YEAR" : interval.contains("month") ? "MONTH"
                : interval.contains("week") ? "WEEK" : interval.contains("day") ? "DAY"
                : interval.contains("hour") ? "HOUR" : null;
        if (interval.contains("one-time")) return NONE;
        return of(num(salaryRange.path("min")), num(salaryRange.path("max")), salaryRange.path("currency").asText(null), period);
    }

    /**
     * Ashby {@code compensation} (requested with {@code includeCompensation=true}): uses the first "Salary"
     * component from {@code summaryComponents}, falling back to the first tier's components.
     */
    public static SalaryInfo fromAshby(JsonNode compensation) {
        if (compensation == null || compensation.isMissingNode() || compensation.isNull()) return NONE;
        SalaryInfo s = firstSalary(compensation.path("summaryComponents"));
        if (s.present()) return s;
        for (JsonNode tier : compensation.path("compensationTiers")) {
            s = firstSalary(tier.path("components"));
            if (s.present()) return s;
        }
        return NONE;
    }

    private static SalaryInfo firstSalary(JsonNode components) {
        for (JsonNode c : components) {
            if (!"Salary".equalsIgnoreCase(c.path("compensationType").asText())) continue;
            String interval = c.path("interval").asText("").toUpperCase(Locale.ROOT);
            String period = interval.contains("YEAR") ? "YEAR" : interval.contains("MONTH") ? "MONTH"
                    : interval.contains("WEEK") ? "WEEK" : interval.contains("DAY") ? "DAY"
                    : interval.contains("HOUR") ? "HOUR" : null;
            SalaryInfo s = of(num(c.path("minValue")), num(c.path("maxValue")), c.path("currencyCode").asText(null), period);
            if (s.present()) return s;
        }
        return NONE;
    }

    private static Number num(JsonNode n) { return n == null || !n.isNumber() ? null : n.numberValue(); }

    private static Integer positive(Number n) {
        if (n == null) return null;
        long v = Math.round(n.doubleValue());
        return v > 0 && v <= Integer.MAX_VALUE ? (int) v : null;
    }
}
