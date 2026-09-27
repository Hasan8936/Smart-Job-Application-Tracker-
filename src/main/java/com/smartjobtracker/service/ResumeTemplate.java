package com.smartjobtracker.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Resume layouts offered by the builder. All are single-column, text-only (no tables, images,
 * icons or text boxes) and use standard section headings, so the exported PDF stays machine-readable.
 *
 * <p>JAKES and SB2NOV are modelled on MIT-licensed LaTeX templates; their generated .tex keeps the
 * original license notice. COMPACT is an original layout.
 */
public enum ResumeTemplate {

    JAKES("jakes", "Jake's Resume",
            "Classic single-column layout: centered name, ruled section headings, role and dates on one line with the company below.",
            "Based on Jake's Resume by Jake Gutierrez (github.com/jakegut/resume), MIT License."),
    SB2NOV("sb2nov", "sb2nov Classic",
            "The original layout Jake's Resume is derived from: bold company, italic role, right-aligned dates, sans-serif type.",
            "Based on the resume template by Sourabh Bajaj (github.com/sb2nov/resume), MIT License."),
    COMPACT("compact", "Minimal Compact",
            "Denser single-column layout with tighter margins and one-line entries, for fitting more on one page.",
            null);

    private final String id;
    private final String displayName;
    private final String description;
    private final String attribution;

    ResumeTemplate(String id, String displayName, String description, String attribution) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.attribution = attribution;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    /** License/credit line for third-party-derived templates; null for original ones. */
    public String attribution() { return attribution; }

    public static final ResumeTemplate DEFAULT = JAKES;

    public static Optional<ResumeTemplate> fromId(String id) {
        if (id == null || id.isBlank()) return Optional.of(DEFAULT);
        String key = id.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.id.equals(key)).findFirst();
    }
}
