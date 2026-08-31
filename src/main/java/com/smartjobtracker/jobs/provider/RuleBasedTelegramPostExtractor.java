package com.smartjobtracker.jobs.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Used only when Gemini extraction isn't configured or fails after retries (see
 * TelegramJobProvider). Deliberately conservative: only fills a field when a clear
 * "Label: value" line is present. Company and location are left null far more often than
 * the Gemini path would — a plain keyword scan can't reliably tell a company name from
 * surrounding text, and guessing would violate the no-fabrication policy applied elsewhere
 * in this app (ResumeApplicationPreparationService, InterviewHeuristic).
 */
@Component
public class RuleBasedTelegramPostExtractor implements TelegramPostExtractor {
    private static final Pattern TITLE_LINE = Pattern.compile("(?im)^\\s*(?:role|position|job title|hiring for)\\s*[:\\-]\\s*(.+)$");
    private static final Pattern COMPANY_LINE = Pattern.compile("(?im)^\\s*company\\s*[:\\-]\\s*(.+)$");
    private static final Pattern LOCATION_LINE = Pattern.compile("(?im)^\\s*location\\s*[:\\-]\\s*(.+)$");
    private static final Pattern EMPLOYMENT_TYPE = Pattern.compile("(?i)\\b(full[- ]?time|part[- ]?time|internship|contract|remote)\\b");
    private static final Pattern BARE_URL = Pattern.compile("https?://\\S+");

    @Override
    public Extraction extract(Input input) {
        String text = input.text() == null ? "" : input.text();
        String title = firstGroup(TITLE_LINE, text);
        String company = firstGroup(COMPANY_LINE, text);
        String location = firstGroup(LOCATION_LINE, text);
        // Strip the Location line before scanning for employment type: "remote" is a
        // legitimate location value and shouldn't be picked up as the employment type just
        // because it appears earlier in the text than an actual "Full-time"/"Contract" label.
        String employmentType = firstGroup(EMPLOYMENT_TYPE, LOCATION_LINE.matcher(text).replaceAll(""));
        String applyUrl = firstLink(input.links(), text);
        // Low, fixed confidence: this path never claims to be sure about anything it finds.
        return new Extraction(company, title, location, employmentType, applyUrl, 0.35, "rules");
    }

    private String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? clean(matcher.group(matcher.groupCount() >= 1 ? 1 : 0)) : null;
    }

    private String firstLink(List<String> links, String text) {
        if (links != null && !links.isEmpty()) return links.get(0);
        Matcher matcher = BARE_URL.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }
}
