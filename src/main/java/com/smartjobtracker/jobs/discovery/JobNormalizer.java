package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.jobs.provider.JobProvider.JobPostingCandidate;
import com.smartjobtracker.model.JobPosting;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Component
public class JobNormalizer {
    public JobPosting normalize(JobPostingCandidate source) {
        JobPosting target = new JobPosting();
        target.setProvider(clean(source.provider())); target.setExternalId(clean(source.externalId()));
        target.setCompany(clean(source.company())); target.setTitle(clean(source.title()));
        target.setLocation(clean(source.location())); target.setEmploymentType(clean(source.employmentType()));
        target.setWorkMode(clean(source.workMode())); target.setApplyUrl(clean(source.applyUrl()));
        target.setPostedAt(parseDate(source.postedAt())); target.setDescription(stripMarkup(source.description()));
        target.setLogoUrl(clean(source.logoUrl())); target.setSalaryMin(source.salaryMin()); target.setSalaryMax(source.salaryMax());
        target.setSalaryCurrency(clean(source.salaryCurrency())); target.setRawJson(source.rawJson());
        target.setDedupeHash(hash(normalizeKey(source.company()) + "|" + normalizeKey(source.title()) + "|" + normalizeKey(source.location())));
        return target;
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String stripMarkup(String value) { return clean(value == null ? null : value.replaceAll("(?s)<[^>]*>", " ").replaceAll("\\s+", " ")); }
    private OffsetDateTime parseDate(String value) { try { return value == null ? null : OffsetDateTime.parse(value); } catch (DateTimeParseException e) { return null; } }
    private String normalizeKey(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim(); }
    private String hash(String value) { try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format("%02x", b)); return out.toString(); } catch (Exception e) { throw new IllegalStateException("Could not hash job identity", e); } }
}