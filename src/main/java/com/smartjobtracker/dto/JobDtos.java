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
    public record JobSummary(Long id, String provider, String company, String title, String location,
                             String employmentType, String workMode, String applyUrl, OffsetDateTime postedAt, String logoUrl) {
        public static JobSummary from(JobPosting p) { return new JobSummary(p.getId(), p.getProvider(), p.getCompany(), p.getTitle(), p.getLocation(), p.getEmploymentType(), p.getWorkMode(), p.getApplyUrl(), p.getPostedAt(), p.getLogoUrl()); }
    }
    public record JobDetail(Long id, String provider, String company, String title, String location,
                            String employmentType, String workMode, String applyUrl, OffsetDateTime postedAt,
                            String description, String logoUrl, Integer salaryMin, Integer salaryMax,
                            String salaryCurrency, java.util.List<String> requiredSkills,
                            java.util.List<String> preferredSkills) {
        public static JobDetail from(JobPosting p, java.util.List<String> requiredSkills, java.util.List<String> preferredSkills) {
            return new JobDetail(p.getId(), p.getProvider(), p.getCompany(), p.getTitle(), p.getLocation(), p.getEmploymentType(), p.getWorkMode(), p.getApplyUrl(), p.getPostedAt(), p.getDescription(), p.getLogoUrl(), p.getSalaryMin(), p.getSalaryMax(), p.getSalaryCurrency(), requiredSkills, preferredSkills);
        }
    }
}