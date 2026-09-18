package com.smartjobtracker.dto;

import com.smartjobtracker.model.JobPosting;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public final class JobDtos {
    private JobDtos() {}
    public record DiscoverRequest(@Size(max = 200) String keywords,
                                   @Size(max = 20) java.util.List<@Size(max = 100) String> roles,
                                   @Size(max = 20) java.util.List<@Size(max = 100) String> locations) {}
    public record DiscoverResponse(int synchronizedJobs, java.util.Map<String, String> providerErrors) {}
    public record AsyncDiscoverResponse(String syncId) {}
    public record SyncProgressDto(String status, String currentProvider, int providerJobs, int totalSaved, boolean done, java.util.Map<String, String> errors) {}
    public record JobSummary(Long id, String provider, String company, String title, String location,
                             String employmentType, String workMode, String applyUrl, OffsetDateTime postedAt, String logoUrl,
                             Integer salaryMin, Integer salaryMax, String salaryCurrency, Boolean salaryEstimated,
                             String descriptionSnippet, Integer matchScore, java.util.List<String> skills) {
        public static JobSummary from(JobPosting p) {
            return from(p, null, java.util.List.of());
        }
        public static JobSummary from(JobPosting p, Integer matchScore, java.util.List<String> skills) {
            String raw = p.getDescription();
            String snippet = raw == null ? null : (raw.length() > 180 ? raw.substring(0, 180) + "…" : raw);
            return new JobSummary(p.getId(), p.getProvider(), p.getCompany(), p.getTitle(), p.getLocation(),
                p.getEmploymentType(), p.getWorkMode(), p.getApplyUrl(), p.getPostedAt(), p.getLogoUrl(),
                p.getSalaryMin(), p.getSalaryMax(), p.getSalaryCurrency(),
                Boolean.TRUE.equals(p.getSalaryEstimated()), snippet, matchScore, skills);
        }
    }
    public record JobDetail(Long id, String provider, String company, String title, String location,
                            String employmentType, String workMode, String applyUrl, OffsetDateTime postedAt,
                            String description, String logoUrl, Integer salaryMin, Integer salaryMax,
                            String salaryCurrency, Boolean salaryEstimated,
                            java.util.List<String> requiredSkills, java.util.List<String> preferredSkills) {
        public static JobDetail from(JobPosting p, java.util.List<String> requiredSkills, java.util.List<String> preferredSkills) {
            return new JobDetail(p.getId(), p.getProvider(), p.getCompany(), p.getTitle(), p.getLocation(),
                p.getEmploymentType(), p.getWorkMode(), p.getApplyUrl(), p.getPostedAt(), p.getDescription(),
                p.getLogoUrl(), p.getSalaryMin(), p.getSalaryMax(), p.getSalaryCurrency(),
                Boolean.TRUE.equals(p.getSalaryEstimated()), requiredSkills, preferredSkills);
        }
    }
}