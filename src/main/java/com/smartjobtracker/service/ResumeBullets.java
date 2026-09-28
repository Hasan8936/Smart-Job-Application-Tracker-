package com.smartjobtracker.service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits extracted resume text into achievement bullets with the role/project heading they sit under, and picks the
 * bullet most relevant to a question. Used by the offline interview-prep generator so its draft answers restate the
 * candidate's own resume lines (never invented content) instead of generic advice.
 */
final class ResumeBullets {

    enum Section { EXPERIENCE, PROJECTS, OTHER }

    /** tech: the "Tech Stack:" line under the same role/project, which bullets often don't repeat. */
    record Bullet(String heading, Section section, String text, String tech) {
        boolean hasMetric() { return METRIC.matcher(text).find(); }
    }

    private static final Pattern SECTION_HEADER = Pattern.compile(
            "(?i)^(work experience|professional experience|experience|internships?|employment|projects?|academic projects|"
            + "personal projects|education|skills|technical skills|certifications?|achievements|awards|summary|profile|"
            + "objective|publications|languages|interests|extracurricular.*|positions of responsibility)\\s*:?$");
    private static final Pattern BULLET_MARK = Pattern.compile("^[-•*▪►●◦‣–]\\s*|^o\\s+(?=[A-Z])");
    private static final Pattern METRIC = Pattern.compile("\\d[\\d,.]*\\s*(\\+|%|percent|x\\b|ms\\b|s\\b|k\\b|K\\b|m\\b|M\\b|gb\\b|GB\\b|tb\\b|TB\\b|users|requests|concurrent)|\\bfrom\\s+\\S+\\s+to\\s+\\S+");
    private static final Pattern DATES = Pattern.compile(
            "(?i)\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?\\s+\\d{4}|\\b\\d{4}\\s*[-–—]\\s*(\\d{4}|present|current)\\b|\\bpresent\\b|\\bcurrent\\b|\\b(19|20)\\d{2}\\b");
    private static final Set<String> STOP = Set.of("the", "and", "for", "with", "that", "this", "your", "you", "have",
            "from", "into", "using", "what", "when", "how", "about", "tell", "time", "describe", "would", "which", "were",
            "was", "our", "are", "will", "role", "work", "team", "their", "they", "them", "over", "more", "than");
    private static final Set<String> IRREGULAR_PAST = Set.of("built", "led", "wrote", "ran", "made", "drove", "won", "set",
            "taught", "grew", "cut", "began", "took", "spun", "shipped", "owned", "kept", "held", "met", "sped", "rebuilt", "rewrote");

    private final List<Bullet> bullets;

    private ResumeBullets(List<Bullet> bullets) { this.bullets = bullets; }

    List<Bullet> all() { return bullets; }
    boolean isEmpty() { return bullets.isEmpty(); }

    static ResumeBullets from(InterviewPrepProvider.FactProfile facts) {
        List<Bullet> parsed = facts == null ? List.of() : parse(facts.resumeText());
        if (!parsed.isEmpty()) return new ResumeBullets(parsed);
        // No raw text (or no bullet structure): fall back to the extractor's experience/project lines.
        List<Bullet> fallback = new ArrayList<>();
        if (facts != null) {
            addLines(fallback, facts.experience(), Section.EXPERIENCE);
            addLines(fallback, facts.projects(), Section.PROJECTS);
        }
        return new ResumeBullets(fallback);
    }

    private static void addLines(List<Bullet> out, String text, Section section) {
        if (text == null) return;
        for (String line : text.split("\\n+")) {
            String l = BULLET_MARK.matcher(line.trim()).replaceFirst("").trim();
            if (l.length() >= 25) out.add(new Bullet("", section, sentence(l), ""));
        }
    }

    static List<Bullet> parse(String text) {
        List<Bullet> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        Section section = Section.OTHER;
        boolean inIgnoredSection = false;
        String heading = "";
        String tech = "";
        StringBuilder current = null;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (SECTION_HEADER.matcher(line).matches()) {
                flush(out, current, heading, section, tech);
                current = null;
                String h = line.toLowerCase(Locale.ROOT);
                section = h.contains("project") ? Section.PROJECTS
                        : (h.contains("experience") || h.contains("intern") || h.contains("employment")) ? Section.EXPERIENCE : Section.OTHER;
                inIgnoredSection = section == Section.OTHER;
                heading = "";
                tech = "";
                continue;
            }
            if (inIgnoredSection) continue;
            Matcher mark = BULLET_MARK.matcher(line);
            if (mark.find()) {
                flush(out, current, heading, section, tech);
                current = new StringBuilder(line.substring(mark.end()).trim());
            } else if (current != null && Character.isLowerCase(line.charAt(0))) {
                current.append(' ').append(line); // PDF line-wrap continuation of the previous bullet
            } else if (TECH_LINE.matcher(line).find()) {
                flush(out, current, heading, section, tech);
                current = null;
                tech = line.substring(line.indexOf(':') + 1).trim();
            } else {
                flush(out, current, heading, section, tech);
                current = null;
                if (line.length() <= 120) { heading = cleanHeading(line); tech = ""; }
            }
        }
        flush(out, current, heading, section, tech);
        return out;
    }

    private static final Pattern TECH_LINE = Pattern.compile("(?i)^(tech stack|technologies|tools|stack)\\s*:");

    private static void flush(List<Bullet> out, StringBuilder current, String heading, Section section, String tech) {
        if (current == null) return;
        String text = current.toString().replaceAll("\\s+", " ").trim();
        if (text.length() >= 20) out.add(new Bullet(heading, section, sentence(text), tech));
    }

    private static String cleanHeading(String line) {
        String h = DATES.matcher(line).replaceAll(" ").replaceAll("\\[[^]]*]", " ")
                .replaceAll("\\s*[-–—|,]\\s*$", "").replaceAll("\\s+", " ").trim();
        return h.replaceAll("\\s*[-–—|,]\\s*$", "").trim();
    }

    private static String sentence(String text) {
        String t = text.trim();
        return t.endsWith(".") || t.endsWith("!") ? t : t + ".";
    }

    /** Best bullet for the hint words; bullets already used are only reused when nothing else is relevant. */
    Optional<Bullet> best(Collection<String> hints, Set<Bullet> used) {
        Bullet best = null; int bestScore = -1;
        for (Bullet b : bullets) {
            int score = score(b, hints) - (used.contains(b) ? 5 : 0);
            if (score > bestScore) { bestScore = score; best = b; }
        }
        return Optional.ofNullable(best);
    }

    /** Bullets that literally mention the phrase (e.g. a skill), strongest first. */
    List<Bullet> mentioning(String phrase) {
        List<Bullet> inText = new ArrayList<>(), inStack = new ArrayList<>();
        for (Bullet b : bullets) {
            if (mentions(b.text() + " " + b.heading(), phrase)) inText.add(b);
            else if (mentions(b.tech(), phrase)) inStack.add(b);   // used in that role/project per its tech stack
        }
        inText.sort(Comparator.comparing((Bullet b) -> !b.hasMetric()));
        inStack.sort(Comparator.comparing((Bullet b) -> !b.hasMetric()));
        inText.addAll(inStack);
        return inText;
    }

    /** Whole-word, case-insensitive: "java" must not match "javascript", "sql" not "postgresql". */
    static boolean mentions(String haystack, String phrase) {
        if (haystack == null || phrase == null || phrase.isBlank()) return false;
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(phrase.toLowerCase(Locale.ROOT)) + "(?![a-z0-9])")
                .matcher(haystack.toLowerCase(Locale.ROOT)).find();
    }

    static int score(Bullet b, Collection<String> hints) {
        String text = (b.text() + " " + b.heading() + " " + (b.tech() == null ? "" : b.tech())).toLowerCase(Locale.ROOT);
        int score = b.hasMetric() ? 2 : 0;
        for (String hint : hints) {
            String h = hint.toLowerCase(Locale.ROOT);
            if (h.length() < 3 || STOP.contains(h)) continue;
            if (text.contains(h)) score += h.contains(" ") ? 4 : 3;
        }
        return score;
    }

    /** Words worth matching from free text (question, JD sentence). */
    static List<String> keywords(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9+#.]+")) {
            String t = w.replaceAll("^\\.+|\\.+$", "");
            if (t.length() > 3 && !STOP.contains(t)) out.add(t);
        }
        return out;
    }

    /** "As Backend Engineering Intern at Northwind Payments" / "On my Ride Share Pricing Engine project" / "". */
    static String intro(Bullet b) {
        String h = b.heading() == null ? "" : b.heading().trim();
        if (h.isEmpty()) return "";
        if (b.section() == Section.PROJECTS) {
            String name = h.split("\\s+[-–—|:]\\s+|\\s*\\|\\s*")[0].trim();
            return name.isEmpty() ? "" : "On my " + name + " project";
        }
        String[] parts = h.split("\\s+[-–—|]\\s+|\\s*\\|\\s*|,\\s+", 2);
        if (parts.length == 2 && !parts[1].isBlank()) return "As " + parts[0].trim() + " at " + parts[1].trim();
        return "In my " + h + " role";
    }

    /** "Built a REST API..." → "I built a REST API..."; non-verb-led lines are quoted instead of rewritten. */
    static String firstPerson(Bullet b) {
        String t = b.text().trim();
        String first = t.split("\\s+", 2)[0].replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
        boolean verbLed = first.length() > 2 && (first.endsWith("ed") || IRREGULAR_PAST.contains(first));
        if (verbLed) return "I " + Character.toLowerCase(t.charAt(0)) + t.substring(1);
        return "my resume lists: \"" + t.replaceAll("\\.$", "") + "\".";
    }

    /** The clause carrying the result, e.g. "cutting manual refund handling time by 60%". */
    static String metric(Bullet b) {
        for (String clause : b.text().split(",|;|\\s+and\\s+(?=[a-z]+ing\\b)")) {
            if (METRIC.matcher(clause).find()) return clause.trim().replaceAll("\\.$", "");
        }
        return "";
    }

    /** One grounded sentence: intro + first-person restatement of the bullet. */
    static String restate(Bullet b) {
        String intro = intro(b);
        String body = firstPerson(b);
        return intro.isEmpty() ? Character.toUpperCase(body.charAt(0)) + body.substring(1) : intro + ", " + body;
    }

    static String evidence(Bullet b) {
        String h = b.heading() == null || b.heading().isBlank() ? "" : b.heading() + ": ";
        String v = (h + b.text()).replaceAll("\\s+", " ").trim();
        return v.length() > 300 ? v.substring(0, 300) + "..." : v;
    }
}
