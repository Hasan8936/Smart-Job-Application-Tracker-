package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.model.JobPosting;
import com.smartjobtracker.repository.JobPostingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Estimates a salary for postings that don't state one, using only salaries that other stored postings
 * actually report (from the job source or the posting text).
 *
 * <p>A posting is compared with peers of the same role family (e.g. backend, data science), seniority
 * (intern/junior/mid/senior/staff) and country. When at least {@value #MIN_SAMPLES} peers report a yearly
 * salary in the same currency, the estimate is the median of their minimums to the median of their maximums,
 * stored with {@code salary_source = ESTIMATE} and the sample size. Otherwise the posting has no salary
 * ("unavailable") — nothing is guessed. Re-running is idempotent: estimates are recomputed from current data.
 */
@Component
public class SalaryEstimator {
    private static final Logger log = LoggerFactory.getLogger(SalaryEstimator.class);
    static final int MIN_SAMPLES = 5;

    private final JobPostingRepository repository;

    public SalaryEstimator(JobPostingRepository repository) { this.repository = repository; }

    record PeerKey(String family, String seniority, String country) {}
    record Estimate(int min, int max, String currency, int sampleSize) {}

    /** Recomputes estimates for every posting without a reported salary; returns how many rows changed. */
    @Transactional
    public int refreshEstimates() {
        Map<PeerKey, List<JobPosting>> pool = pool(repository.findWithReportedYearlySalary());
        List<JobPosting> targets = repository.findWithoutReportedSalary();
        List<JobPosting> changed = new ArrayList<>();
        int estimated = 0;
        for (JobPosting job : targets) {
            Optional<Estimate> estimate = estimate(job, pool);
            if (estimate.isPresent()) estimated++;
            if (apply(job, estimate.orElse(null))) changed.add(job);
        }
        repository.saveAll(changed);
        log.info("Salary estimates: pool={} targets={} estimated={} updated={}",
                pool.values().stream().mapToInt(List::size).sum(), targets.size(), estimated, changed.size());
        return changed.size();
    }

    Map<PeerKey, List<JobPosting>> pool(List<JobPosting> reported) {
        Map<PeerKey, List<JobPosting>> pool = new HashMap<>();
        for (JobPosting p : reported) {
            PeerKey key = key(p);
            if (key != null) pool.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        return pool;
    }

    Optional<Estimate> estimate(JobPosting job, Map<PeerKey, List<JobPosting>> pool) {
        PeerKey key = key(job);
        if (key == null) return Optional.empty();
        List<JobPosting> peers = pool.getOrDefault(key, List.of());
        if (peers.size() < MIN_SAMPLES) return Optional.empty();

        // Use the currency most peers report in; never mix currencies.
        String currency = peers.stream().map(JobPosting::getSalaryCurrency).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        List<JobPosting> sameCurrency = peers.stream().filter(p -> Objects.equals(currency, p.getSalaryCurrency())).toList();
        if (currency == null || sameCurrency.size() < MIN_SAMPLES) return Optional.empty();

        List<Integer> mins = new ArrayList<>(), maxes = new ArrayList<>();
        for (JobPosting p : sameCurrency) {
            mins.add(p.getSalaryMin() != null ? p.getSalaryMin() : p.getSalaryMax());
            maxes.add(p.getSalaryMax() != null ? p.getSalaryMax() : p.getSalaryMin());
        }
        int lo = roundTo(median(mins), currency), hi = roundTo(median(maxes), currency);
        if (lo > hi) { int t = lo; lo = hi; hi = t; }
        return Optional.of(new Estimate(lo, hi, currency, sameCurrency.size()));
    }

    /** Writes the estimate, or clears a stale one; returns whether the row changed. Reported salaries are never touched. */
    boolean apply(JobPosting job, Estimate e) {
        String source = job.getSalarySource();
        if ("PROVIDER".equals(source) || "DESCRIPTION".equals(source)) return false;
        // Pre-V25 rows: a salary not flagged as estimated was reported by the job source.
        if (source == null && !Boolean.TRUE.equals(job.getSalaryEstimated())
                && (job.getSalaryMin() != null || job.getSalaryMax() != null)) return false;
        boolean hadEstimate = "ESTIMATE".equals(source) || Boolean.TRUE.equals(job.getSalaryEstimated())
                || job.getSalaryMin() != null || job.getSalaryMax() != null;
        if (e == null) {
            if (!hadEstimate) return false;
            job.setSalaryMin(null); job.setSalaryMax(null); job.setSalaryCurrency(null); job.setSalaryPeriod(null);
            job.setSalarySource(null); job.setSalarySampleSize(null); job.setSalaryEstimated(false);
            return true;
        }
        if ("ESTIMATE".equals(source) && Objects.equals(job.getSalaryMin(), e.min()) && Objects.equals(job.getSalaryMax(), e.max())
                && Objects.equals(job.getSalaryCurrency(), e.currency()) && Objects.equals(job.getSalarySampleSize(), e.sampleSize())) {
            return false;
        }
        job.setSalaryMin(e.min()); job.setSalaryMax(e.max()); job.setSalaryCurrency(e.currency()); job.setSalaryPeriod("YEAR");
        job.setSalarySource("ESTIMATE"); job.setSalarySampleSize(e.sampleSize()); job.setSalaryEstimated(true);
        return true;
    }

    PeerKey key(JobPosting p) {
        String family = roleFamily(p.getTitle());
        String country = country(p.getLocation());
        if (family == null || country == null) return null;
        return new PeerKey(family, seniority(p.getTitle()), country);
    }

    // ─── Title classification ────────────────────────────────────────────────

    private record Rule(String family, Pattern pattern) {}

    /** Ordered: the first match wins, so specific families come before the generic "software". */
    private static final List<Rule> FAMILIES = List.of(
            // Non-engineering roles first, so "Account Executive, AI" or "Sales Engineer" aren't compared with engineers.
            rule("recruiting", "recruit|talent acquisition|sourcer|people partner|\\bhr\\b|human resources"),
            rule("sales", "account executive|sales|business development|\\bbdr\\b|\\bsdr\\b|account manager|partnerships"),
            rule("customer-success", "customer success|customer support|support specialist|implementation manager"),
            rule("marketing", "marketing|growth marketer|content strategist|communications|\\bpr\\b"),
            rule("finance", "accountant|accounting|finance|financial|controller|\\bfp&a\\b|tax|treasury|payroll"),
            rule("legal", "counsel|legal|attorney|lawyer|paralegal|compliance"),
            rule("engineering-manager", "engineering manager|manager,? (software )?engineering|head of engineering|director of engineering"),
            rule("product-manager", "product manager|product owner|\\bpm\\b"),
            rule("design", "designer|\\bux\\b|ui/ux|user experience"),
            rule("data-science", "data scientist|data science"),
            rule("machine-learning", "machine learning|\\bml\\b|deep learning|\\bnlp\\b|computer vision|"
                    + "\\bai (engineer|researcher|scientist)|(engineer|researcher|scientist)\\b.{0,20}\\bai\\b"),
            rule("data-engineering", "data engineer|analytics engineer|etl"),
            rule("analyst", "analyst"),
            rule("security", "security"),
            rule("devops", "devops|site reliability|\\bsre\\b|platform engineer|infrastructure|cloud engineer"),
            rule("mobile", "\\bios\\b|android|mobile"),
            rule("frontend", "front[- ]?end|\\bui engineer"),
            rule("backend", "back[- ]?end"),
            rule("fullstack", "full[- ]?stack"),
            rule("qa", "\\bqa\\b|quality|\\btest|sdet"),
            rule("software", "software|developer|\\bsde\\b|programmer|engineer"));

    private static Rule rule(String family, String regex) {
        return new Rule(family, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    static String roleFamily(String title) {
        if (title == null) return null;
        for (Rule r : FAMILIES) if (r.pattern().matcher(title).find()) return r.family();
        return null;
    }

    private static final Pattern INTERN = Pattern.compile("intern|trainee|apprentice|co-?op\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern STAFF = Pattern.compile("staff|principal|distinguished|fellow|architect|director|head of|\\bvp\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENIOR = Pattern.compile("senior|\\bsr\\.?\\b|\\blead\\b|\\biii\\b|\\biv\\b|\\bl[5-6]\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern JUNIOR = Pattern.compile("junior|\\bjr\\.?\\b|entry|graduate|new grad|associate|\\bi\\b|\\bl[1-2]\\b", Pattern.CASE_INSENSITIVE);

    static String seniority(String title) {
        if (title == null) return "mid";
        if (INTERN.matcher(title).find()) return "intern";
        if (STAFF.matcher(title).find()) return "staff";
        if (SENIOR.matcher(title).find()) return "senior";
        if (JUNIOR.matcher(title).find()) return "junior";
        return "mid";
    }

    // ─── Location → country ──────────────────────────────────────────────────

    private static final Map<String, List<String>> COUNTRY_WORDS = Map.ofEntries(
            Map.entry("US", List.of("united states", "usa", "u.s.", "san francisco", "new york", "nyc", "seattle", "austin",
                    "boston", "chicago", "los angeles", "denver", "atlanta", "menlo park", "palo alto", "mountain view",
                    "sunnyvale", "san jose", "bellevue", "washington, dc", "washington dc", "washington, d.c.", "washington d.c.",
                    "sf", "bay area", "brooklyn", "miami", "dallas", "houston", "philadelphia", "san diego", "portland", "pittsburgh", "salt lake city")),
            Map.entry("IN", List.of("india", "bengaluru", "bangalore", "hyderabad", "pune", "mumbai", "new delhi", "delhi",
                    "gurgaon", "gurugram", "noida", "chennai", "kolkata", "ahmedabad", "kochi", "jaipur")),
            Map.entry("GB", List.of("united kingdom", "england", "scotland", "london", "manchester", "edinburgh", "cambridge, uk")),
            Map.entry("CA", List.of("canada", "toronto", "vancouver", "montreal", "ottawa", "waterloo", "calgary")),
            Map.entry("DE", List.of("germany", "deutschland", "berlin", "munich", "münchen", "hamburg", "frankfurt")),
            Map.entry("FR", List.of("france", "paris")),
            Map.entry("NL", List.of("netherlands", "amsterdam")),
            Map.entry("IE", List.of("ireland", "dublin")),
            Map.entry("ES", List.of("spain", "madrid", "barcelona")),
            Map.entry("PL", List.of("poland", "warsaw", "krakow", "kraków")),
            Map.entry("SG", List.of("singapore")),
            Map.entry("AU", List.of("australia", "sydney", "melbourne")),
            Map.entry("JP", List.of("japan", "tokyo")),
            Map.entry("BR", List.of("brazil", "são paulo", "sao paulo")),
            Map.entry("MX", List.of("mexico", "méxico")),
            Map.entry("AE", List.of("united arab emirates", "dubai", "abu dhabi")),
            Map.entry("RO", List.of("romania", "bucharest")),
            Map.entry("TW", List.of("taiwan", "taipei")),
            Map.entry("CN", List.of("china", "shanghai", "beijing", "shenzhen")),
            Map.entry("IL", List.of("israel", "tel aviv")),
            Map.entry("SE", List.of("sweden", "stockholm")),
            Map.entry("CH", List.of("switzerland", "zurich", "zürich", "geneva")));

    private static final Set<String> US_STATES = Set.of("AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "ID",
            "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY",
            "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY", "DC");
    private static final Set<String> CA_PROVINCES = Set.of("ON", "BC", "QC", "AB", "MB", "NS", "NB", "SK", "NL", "PE");
    private static final Pattern REGION_CODE = Pattern.compile(",\\s*([A-Z]{2})\\b");
    private static final Pattern US_TOKEN = Pattern.compile("\\b(US|USA)\\b");
    private static final Pattern UK_TOKEN = Pattern.compile("\\bUK\\b");

    /** Country for a free-text location, or null when it can't be told (e.g. plain "Remote"). */
    static String country(String location) {
        if (location == null || location.isBlank()) return null;
        String lower = location.toLowerCase(Locale.ROOT);
        String best = null; int bestAt = Integer.MAX_VALUE;
        // The earliest mention wins, so "London (Remote from Germany)" → GB.
        for (Map.Entry<String, List<String>> e : COUNTRY_WORDS.entrySet()) {
            for (String word : e.getValue()) {
                int at = indexOfWord(lower, word);
                if (at >= 0 && at < bestAt) { bestAt = at; best = e.getKey(); }
            }
        }
        if (best != null) return best;
        Matcher code = REGION_CODE.matcher(location);
        while (code.find()) {
            if (US_STATES.contains(code.group(1))) return "US";
            if (CA_PROVINCES.contains(code.group(1))) return "CA";
        }
        if (US_TOKEN.matcher(location).find()) return "US";
        if (UK_TOKEN.matcher(location).find()) return "GB";
        return null;
    }

    private static int indexOfWord(String haystack, String word) {
        Matcher m = Pattern.compile("(?<![\\p{L}])" + Pattern.quote(word) + "(?![\\p{L}])").matcher(haystack);
        return m.find() ? m.start() : -1;
    }

    // ─── Numbers ─────────────────────────────────────────────────────────────

    static int median(List<Integer> values) {
        List<Integer> sorted = values.stream().filter(Objects::nonNull).sorted(Comparator.naturalOrder()).toList();
        int n = sorted.size();
        if (n == 0) return 0;
        return n % 2 == 1 ? sorted.get(n / 2) : (int) Math.round((sorted.get(n / 2 - 1) + (long) sorted.get(n / 2)) / 2.0);
    }

    /** Estimates are shown rounded so they don't look more precise than they are. */
    static int roundTo(int value, String currency) {
        int step = "INR".equals(currency) || "JPY".equals(currency) ? 10_000 : 1_000;
        return (int) (Math.round(value / (double) step) * step);
    }
}
