package com.smartjobtracker.jobs.discovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rejects job postings written in scripts our users can't read (Arabic by default).
 *
 * <p>A posting is blocked when its title contains any letter from a blocked script, or when more than
 * {@value #DESCRIPTION_THRESHOLD_PERCENT}% of the letters in its description are. Scripts come from
 * {@code app.job-discovery.blocked-scripts} as {@link Character.UnicodeScript} names; ARABIC covers the
 * Arabic, Arabic Supplement, Arabic Extended-A and Arabic Presentation Forms A/B blocks, so Persian and
 * Urdu are included.
 */
@Component
public class ScriptFilter {
    static final int DESCRIPTION_THRESHOLD_PERCENT = 30;

    private final Set<Character.UnicodeScript> blocked;

    public ScriptFilter(@Value("${app.job-discovery.blocked-scripts:ARABIC}") List<String> scriptNames) {
        Set<Character.UnicodeScript> scripts = EnumSet.noneOf(Character.UnicodeScript.class);
        for (String name : scriptNames == null ? List.<String>of() : scriptNames) {
            if (name == null || name.isBlank()) continue;
            try {
                scripts.add(Character.UnicodeScript.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("app.job-discovery.blocked-scripts: unknown Unicode script '" + name.trim()
                        + "' (use Java Character.UnicodeScript names such as ARABIC, HEBREW, CYRILLIC)", e);
            }
        }
        this.blocked = scripts;
    }

    public boolean isBlocked(String title, String description) {
        if (blocked.isEmpty()) return false;
        if (title != null && containsBlockedLetter(title)) return true;
        return description != null && blockedLetterPercent(description) > DESCRIPTION_THRESHOLD_PERCENT;
    }

    private boolean containsBlockedLetter(String text) {
        return text.codePoints().anyMatch(cp -> Character.isLetter(cp) && blocked.contains(Character.UnicodeScript.of(cp)));
    }

    /** Share of letters (not digits, spaces or punctuation) that belong to a blocked script. */
    double blockedLetterPercent(String text) {
        long letters = 0, blockedLetters = 0;
        for (int cp : text.codePoints().toArray()) {
            if (!Character.isLetter(cp)) continue;
            letters++;
            if (blocked.contains(Character.UnicodeScript.of(cp))) blockedLetters++;
        }
        return letters == 0 ? 0 : blockedLetters * 100.0 / letters;
    }
}
