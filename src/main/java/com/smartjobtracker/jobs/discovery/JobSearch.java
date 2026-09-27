package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.model.JobSkill;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The job board's search box. The text is split into words; every word must match the title, company, location, work
 * mode or one of the job's skills, and common shorthands match their long forms ("sde", "frontend", "bengaluru").
 * Results keep the preferred country first, then rank jobs whose title matches more of the words higher.
 */
public final class JobSearch {
    private JobSearch() {}

    /** The only sortable fields; anything else in {@code sort} is ignored. */
    private static final Set<String> SORTABLE = Set.of("postedAt", "createdAt", "title", "company");
    private static final int MAX_TERMS = 6;

    private static final Set<String> STOP_WORDS = Set.of("job", "jobs", "role", "roles", "position", "positions", "opening",
            "openings", "vacancy", "vacancies", "hiring", "in", "at", "for", "the", "a", "an", "and", "or", "of", "with", "near", "me");

    /** Multi-word forms folded into one word before splitting, so "front end" is one term, not "front" + "end". */
    private static final Map<String, String> PHRASES = new LinkedHashMap<>();
    /** Each term matches any of its alternatives. */
    private static final Map<String, List<String>> SYNONYMS = new LinkedHashMap<>();

    static {
        PHRASES.put("front end", "frontend"); PHRASES.put("front-end", "frontend");
        PHRASES.put("back end", "backend"); PHRASES.put("back-end", "backend");
        PHRASES.put("full stack", "fullstack"); PHRASES.put("full-stack", "fullstack");
        PHRASES.put("machine learning", "ml"); PHRASES.put("entry level", "fresher"); PHRASES.put("entry-level", "fresher");
        PHRASES.put("work from home", "remote"); PHRASES.put("react.js", "react"); PHRASES.put("node.js", "node");
        PHRASES.put("software engineer", "sde"); PHRASES.put("software developer", "sde");

        synonyms(List.of("sde", "swe"), "software engineer", "software developer", "sde", "swe", "software development engineer");
        synonyms(List.of("dev", "developer"), "developer", "development", "engineer");
        synonyms(List.of("frontend"), "frontend", "front end", "front-end", "ui developer");
        synonyms(List.of("backend"), "backend", "back end", "back-end");
        synonyms(List.of("fullstack"), "full stack", "fullstack", "full-stack");
        synonyms(List.of("ml"), "machine learning", "ml");
        synonyms(List.of("ai"), "ai", "artificial intelligence", "genai", "llm");
        synonyms(List.of("js"), "javascript", "js");
        synonyms(List.of("reactjs", "react"), "react");
        synonyms(List.of("nodejs", "node"), "node");
        synonyms(List.of("fresher", "freshers", "junior", "graduate", "trainee", "entry"), "fresher", "entry level", "junior", "graduate", "trainee", "associate");
        synonyms(List.of("intern", "internship", "interns"), "intern");
        synonyms(List.of("remote", "wfh"), "remote", "work from home");
        synonyms(List.of("bangalore", "bengaluru", "blr"), "bangalore", "bengaluru");
        synonyms(List.of("gurgaon", "gurugram"), "gurgaon", "gurugram");
        synonyms(List.of("mumbai", "bombay"), "mumbai", "bombay");
        synonyms(List.of("qa", "tester", "testing"), "qa", "quality", "test");
        synonyms(List.of("devops", "sre"), "devops", "site reliability", "sre");
        synonyms(List.of("analyst", "analytics"), "analyst", "analytics");
    }

    private static void synonyms(List<String> terms, String... alternatives) {
        for (String term : terms) SYNONYMS.put(term, List.of(alternatives));
    }

    /** Search terms, each with the alternatives it may match; lower-cased, stop words and duplicates removed. */
    static List<List<String>> terms(String q) {
        if (q == null || q.isBlank()) return List.of();
        String text = " " + q.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim() + " ";
        for (Map.Entry<String, String> phrase : PHRASES.entrySet()) {
            text = text.replace(" " + phrase.getKey() + " ", " " + phrase.getValue() + " ");
        }
        Set<String> words = new LinkedHashSet<>();
        for (String raw : text.split("[\\s,;|/]+")) {
            String word = raw.replaceAll("^[^a-z0-9+#]+|[^a-z0-9+#.]+$|\\.$", "");
            if (word.isEmpty() || STOP_WORDS.contains(word)) continue;
            words.add(word);
        }
        List<List<String>> terms = new ArrayList<>();
        for (String word : words) {
            if (terms.size() == MAX_TERMS) break;
            terms.add(SYNONYMS.getOrDefault(word, List.of(word)));
        }
        return terms;
    }

    /** Filters shared by the full list and the "new since" list. {@code createdAfter} is null for the full list. */
    public record Criteria(String q, String location, String employmentType, String provider,
                           OffsetDateTime postedAfter, OffsetDateTime postedBefore, OffsetDateTime createdAfter,
                           String country, String preferredCountry) {}

    /** Filters, relevance ranking and ordering. Pass the page UNSORTED to the repository; {@code sort} is applied here. */
    public static Specification<JobPosting> spec(Criteria c, Sort sort) {
        List<List<String>> terms = terms(c.q());
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isFalse(root.get("scriptBlocked")));
            for (List<String> alternatives : terms) {
                List<Predicate> any = new ArrayList<>();
                for (String alt : alternatives) any.add(matches(root, query, cb, alt));
                where.add(cb.or(any.toArray(Predicate[]::new)));
            }
            if (c.location() != null) where.add(cb.like(cb.lower(root.get("location")), contains(c.location())));
            if (c.employmentType() != null) where.add(cb.equal(cb.lower(root.get("employmentType")), c.employmentType().toLowerCase(Locale.ROOT)));
            if (c.provider() != null) where.add(cb.equal(cb.lower(root.get("provider")), c.provider().toLowerCase(Locale.ROOT)));
            if (c.postedAfter() != null) where.add(cb.greaterThanOrEqualTo(root.get("postedAt"), c.postedAfter()));
            if (c.postedBefore() != null) where.add(cb.lessThanOrEqualTo(root.get("postedAt"), c.postedBefore()));
            if (c.createdAfter() != null) where.add(cb.greaterThan(root.get("createdAt"), c.createdAfter()));
            if (c.country() != null) where.add(cb.equal(root.get("countryCode"), c.country()));

            // Ordering only on the row query; the page's count query must stay a plain count.
            Class<?> resultType = query.getResultType();
            if (resultType != Long.class && resultType != long.class) {
                query.orderBy(orders(root, cb, c.preferredCountry(), terms, sort));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static List<Order> orders(Root<JobPosting> root, CriteriaBuilder cb, String preferredCountry,
                                      List<List<String>> terms, Sort sort) {
        List<Order> orders = new ArrayList<>();
        if (preferredCountry != null) {
            orders.add(cb.asc(cb.<Integer>selectCase().when(cb.equal(root.get("countryCode"), preferredCountry), 0).otherwise(1)));
        }
        if (!terms.isEmpty()) {
            // Relevance: how many of the search terms appear in the title.
            Expression<Integer> score = cb.literal(0);
            Expression<String> title = paddedTitle(cb, root);
            for (List<String> alternatives : terms) {
                List<Predicate> any = new ArrayList<>();
                for (String alt : alternatives) any.add(cb.like(title, alt.length() <= 3 ? "% " + alt + " %" : contains(alt)));
                score = cb.sum(score, cb.<Integer>selectCase().when(cb.or(any.toArray(Predicate[]::new)), 1).otherwise(0));
            }
            orders.add(cb.desc(score));
        }
        for (Sort.Order o : sort == null ? Sort.unsorted() : sort) {
            if (!SORTABLE.contains(o.getProperty())) continue;
            if (o.getProperty().equals("postedAt")) {
                // Undated postings last whichever direction is chosen.
                orders.add(cb.asc(cb.<Integer>selectCase().when(cb.isNull(root.get("postedAt")), 1).otherwise(0)));
            }
            orders.add(o.isAscending() ? cb.asc(root.get(o.getProperty())) : cb.desc(root.get(o.getProperty())));
        }
        orders.add(cb.desc(root.get("id"))); // stable paging
        return orders;
    }

    /**
     * One alternative matches the title, company, location, work mode, or a skill of the job. Short words ("ai", "go",
     * "qa") must be whole words in the title and exact skill names, so "go" doesn't match every "Google" posting.
     */
    private static Predicate matches(Root<JobPosting> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
                                     CriteriaBuilder cb, String alternative) {
        boolean shortWord = alternative.length() <= 3;
        Subquery<Long> skill = query.subquery(Long.class);
        Root<JobSkill> s = skill.from(JobSkill.class);
        skill.select(s.get("jobPostingId")).where(
                cb.equal(s.get("jobPostingId"), root.get("id")),
                shortWord ? cb.equal(s.get("normalizedName"), alternative) : cb.like(s.get("normalizedName"), contains(alternative)));
        if (shortWord) {
            return cb.or(cb.like(paddedTitle(cb, root), "% " + alternative + " %"),
                    cb.equal(cb.lower(root.get("company")), alternative),
                    cb.exists(skill));
        }
        return cb.or(
                cb.like(cb.lower(cb.coalesce(root.get("title"), "")), contains(alternative)),
                cb.like(cb.lower(root.get("company")), contains(alternative)),
                cb.like(cb.lower(cb.coalesce(root.get("location"), "")), contains(alternative)),
                cb.like(cb.lower(cb.coalesce(root.get("workMode"), "")), contains(alternative)),
                cb.exists(skill));
    }

    /** " title " lower-cased with common separators turned into spaces, for whole-word matching. */
    private static Expression<String> paddedTitle(CriteriaBuilder cb, Root<JobPosting> root) {
        Expression<String> title = cb.lower(cb.coalesce(root.get("title"), ""));
        for (String separator : List.of("/", "(", ")", ",", "-", "|")) {
            title = cb.function("replace", String.class, title, cb.literal(separator), cb.literal(" "));
        }
        return cb.concat(cb.concat(" ", title), " ");
    }

    private static String contains(String value) {
        String escaped = value.toLowerCase(Locale.ROOT).replace("\\", "").replace("%", "").replace("_", "");
        return "%" + escaped + "%";
    }
}
