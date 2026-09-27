package com.smartjobtracker.jobs.discovery;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A short preview of a job description that says something about the job. Descriptions usually open with company
 * boilerplate ("About Acme … we're building …"), so the preview starts at the first role-focused section heading
 * ("About the role", "Responsibilities", "What you'll do", …) when there is one, and otherwise skips a leading
 * "About &lt;company&gt;" paragraph. Only text from the description is used.
 */
public final class DescriptionSnippet {
    private DescriptionSnippet() {}

    public static final int DEFAULT_LENGTH = 180;

    /** A line that is just a role-section heading (optionally ending in ':' or '-'). */
    private static final Pattern ROLE_HEADING = Pattern.compile(
            "(?im)^[\\s•*#-]*(about (the|this) (role|job|position|opportunity)|the role|role (overview|summary|description)|"
                    + "job (description|summary|overview|responsibilities)|position (summary|overview)|the opportunity|your role|"
                    + "in this role(,? you will)?|what you('|’)?ll do|what you will do|what you('|’)?ll be doing|key responsibilities|"
                    + "responsibilities|roles? (and|&) responsibilities|the job|overview of the role|what we('|’)?re looking for)"
                    + "\\s*[:\\-–—]?\\s*$");
    private static final Pattern COMPANY_INTRO = Pattern.compile("(?i)^\\s*(about (us|the company|[A-Z][\\w&.' -]{1,40})\\b|who we are|our (story|mission|company))");

    public static String of(String description) { return of(description, DEFAULT_LENGTH); }

    public static String of(String description, int maxLength) {
        if (description == null || description.isBlank()) return null;
        String text = description.strip();

        Matcher heading = ROLE_HEADING.matcher(text);
        if (heading.find() && heading.end() < text.length() - 40) {
            text = text.substring(heading.end());
        } else {
            String[] paragraphs = text.split("\\n\\s*\\n", 2);
            if (paragraphs.length == 2 && COMPANY_INTRO.matcher(paragraphs[0]).find() && paragraphs[1].strip().length() > 60) {
                text = paragraphs[1];
            }
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        if (flat.length() <= maxLength) return flat;
        int cut = flat.lastIndexOf(' ', maxLength);
        return flat.substring(0, cut > maxLength / 2 ? cut : maxLength) + "…";
    }
}
