package com.smartjobtracker.dto;

import com.smartjobtracker.model.JobPosting;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class JobDtos {
    private JobDtos() {}
    public record DiscoverRequest(@Size(max = 200) String keywords,
                                   @Size(max = 20) java.util.List<@Size(max = 100) String> roles,
                                   @Size(max = 20) java.util.List<@Size(max = 100) String> locations,
                                   @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(720) Integer postedWithinHours) {}
    public record DiscoverResponse(int synchronizedJobs, java.util.Map<String, String> providerErrors) {}
    public record AsyncDiscoverResponse(String syncId) {}
    /** {@code upToDate}: sources skipped because the same search was synced moments ago. */
    public record SyncProgressDto(String status, String currentProvider, int providerJobs, int totalSaved, boolean done,
                                  java.util.Map<String, String> errors, java.util.List<String> upToDate) {}
    public record JobSummary(Long id, String provider, String company, String title, String location,
                             String employmentType, String workMode, String applyUrl, OffsetDateTime postedAt, String logoUrl,
                             Integer salaryMin, Integer salaryMax, String salaryCurrency, Boolean salaryEstimated,
                             String descriptionSnippet, Integer matchScore, java.util.List<String> skills,
                             String salaryPeriod, String salarySource, Integer salarySampleSize, String countryCode) {
        public static JobSummary from(JobPosting p) {
            return from(p, null, java.util.List.of());
        }
        public static JobSummary from(JobPosting p, Integer matchScore, java.util.List<String> skills) {
            String snippet = com.smartjobtracker.jobs.discovery.DescriptionSnippet.of(p.getDescription());
            return new JobSummary(p.getId(), p.getProvider(), p.getCompany(), p.getTitle(), p.getLocation(),
                p.getEmploymentType(), p.getWorkMode(), p.getApplyUrl(), p.getPostedAt(), p.getLogoUrl(),
                p.getSalaryMin(), p.getSalaryMax(), p.getSalaryCurrency(),
                Boolean.TRUE.equals(p.getSalaryEstimated()), snippet, matchScore, skills,
                p.getSalaryPeriod(), p.getSalarySource(), p.getSalarySampleSize(), p.getCountryCode());
        }
    }
    public record JobDetail(Long id, String provider, String company, String title, String location,
                            String employmentType, String workMode, String applyUrl, OffsetDateTime postedAt,
                            String description, String logoUrl, Integer salaryMin, Integer salaryMax,
                            String salaryCurrency, Boolean salaryEstimated,
                            java.util.List<String> requiredSkills, java.util.List<String> preferredSkills,
                            String salaryPeriod, String salarySource, Integer salarySampleSize, String countryCode) {
        public static JobDetail from(JobPosting p, java.util.List<String> requiredSkills, java.util.List<String> preferredSkills) {
            return new JobDetail(p.getId(), p.getProvider(), p.getCompany(), p.getTitle(), p.getLocation(),
                p.getEmploymentType(), p.getWorkMode(), p.getApplyUrl(), p.getPostedAt(), p.getDescription(),
                p.getLogoUrl(), p.getSalaryMin(), p.getSalaryMax(), p.getSalaryCurrency(),
                Boolean.TRUE.equals(p.getSalaryEstimated()), requiredSkills, preferredSkills,
                p.getSalaryPeriod(), p.getSalarySource(), p.getSalarySampleSize(), p.getCountryCode());
        }
    }
}