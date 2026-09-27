package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.jobs.provider.JobProvider.JobPostingCandidate;
import com.smartjobtracker.jobs.provider.SalaryInfo;
import com.smartjobtracker.model.JobPosting;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Component
public class JobNormalizer {
    private final SalaryTextParser salaryParser = new SalaryTextParser();

    public JobPosting normalize(JobPostingCandidate source) {
        JobPosting target = new JobPosting();
        target.setProvider(clean(source.provider())); target.setExternalId(clean(source.externalId()));
        target.setCompany(clean(source.company())); target.setTitle(clean(source.title()));
        target.setEmploymentType(clean(source.employmentType()));
        target.setWorkMode(clean(source.workMode())); target.setApplyUrl(clean(source.applyUrl()));
        target.setLocation(location(source.location(), target.getWorkMode()));
        target.setCountryCode(CountryDetector.country(target.getLocation()));
        target.setPostedAt(parseDate(source.postedAt())); target.setDescription(toPlainText(source.description()));
        target.setLogoUrl(clean(source.logoUrl())); target.setRawJson(source.rawJson());
        applySalary(target, source);
        target.setDedupeHash(hash(normalizeKey(source.company()) + "|" + normalizeKey(source.title()) + "|" + normalizeKey(source.location())));
        return target;
    }

    /** Salary the source reports wins; otherwise one stated in the description. Never invented here. */
    private void applySalary(JobPosting target, JobPostingCandidate source) {
        SalaryInfo salary = SalaryInfo.of(source.salaryMin(), source.salaryMax(), source.salaryCurrency(), clean(source.salaryPeriod()));
        String origin = "PROVIDER";
        if (!salary.present()) {
            salary = salaryParser.parse(target.getDescription());
            origin = "DESCRIPTION";
        }
        if (!salary.present()) return;
        target.setSalaryMin(salary.min()); target.setSalaryMax(salary.max());
        target.setSalaryCurrency(salary.currency()); target.setSalaryPeriod(salary.period());
        target.setSalarySource(origin); target.setSalaryEstimated(false);
    }

    private String location(String raw, String workMode) {
        String location = clean(raw);
        if (location == null && workMode != null && workMode.toLowerCase(Locale.ROOT).contains("remote")) return "Remote";
        return location;
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    /**
     * Job boards send HTML, sometimes entity-escaped (Greenhouse sends "&lt;p&gt;"). Unescape, keep paragraph
     * and list breaks as newlines so the description stays readable, then drop the remaining tags.
     */
    String toPlainText(String value) {
        if (value == null || value.isBlank()) return null;
        String text = HtmlUtils.htmlUnescape(value);
        text = text.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<li[^>]*>", "\n• ")
                .replaceAll("(?i)<br\\s*/?>|</(p|div|ul|ol|h[1-6]|tr|section)>", "\n")
                .replaceAll("(?s)<[^>]*>", " ");
        text = HtmlUtils.htmlUnescape(text).replace(' ', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n");
        return clean(text);
    }

    /** ISO timestamps (with or without offset), plain dates, and epoch seconds/millis (Lever). Unparseable → null. */
    OffsetDateTime parseDate(String value) {
        String v = clean(value);
        if (v == null) return null;
        try { return OffsetDateTime.parse(v); } catch (DateTimeParseException ignored) { }
        try { return LocalDateTime.parse(v).atOffset(ZoneOffset.UTC); } catch (DateTimeParseException ignored) { }
        try { return LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v).atStartOfDay().atOffset(ZoneOffset.UTC); } catch (DateTimeParseException ignored) { }
        if (v.matches("\\d{9,13}")) {
            long n = Long.parseLong(v);
            Instant instant = v.length() >= 12 ? Instant.ofEpochMilli(n) : Instant.ofEpochSecond(n);
            return instant.atOffset(ZoneOffset.UTC);
        }
        return null;
    }

    private String normalizeKey(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim(); }
    private String hash(String value) { try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format("%02x", b)); return out.toString(); } catch (Exception e) { throw new IllegalStateException("Could not hash job identity", e); } }
}
