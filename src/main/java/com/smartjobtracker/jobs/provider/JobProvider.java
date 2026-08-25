package com.smartjobtracker.jobs.provider;

import java.util.List;
import java.util.Set;

public interface JobProvider {
    String id();
    boolean isEnabled();
    Set<Capability> capabilities();
    JobBatch search(JobQuery query, String cursor);
    ProviderJob fetchJobDetails(String externalId);
    default JobPostingCandidate normalize(ProviderJob job) {
        return JobPostingCandidate.from(job, id());
    }

    enum Capability { SALARY, LOGO, POSTED_DATE, OFFICIAL_APPLY_URL }

    record JobQuery(String keywords, List<String> roles, List<String> locations) {
        public JobQuery {
            roles = roles == null ? List.of() : List.copyOf(roles);
            locations = locations == null ? List.of() : List.copyOf(locations);
        }
    }

    record JobBatch(List<ProviderJob> jobs, String nextCursor) {
        public JobBatch { jobs = jobs == null ? List.of() : List.copyOf(jobs); }
    }

    record ProviderJob(String externalId, String company, String title, String location,
                       String employmentType, String workMode, String applyUrl,
                       String postedAt, String description, String logoUrl,
                       Integer salaryMin, Integer salaryMax, String salaryCurrency,
                       String rawJson) {}

    record JobPostingCandidate(String provider, String externalId, String company, String title,
                               String location, String employmentType, String workMode,
                               String applyUrl, String postedAt, String description, String logoUrl,
                               Integer salaryMin, Integer salaryMax, String salaryCurrency,
                               String rawJson) {
        public static JobPostingCandidate from(ProviderJob job, String provider) {
            return new JobPostingCandidate(provider, job.externalId(), job.company(), job.title(),
                    job.location(), job.employmentType(), job.workMode(), job.applyUrl(), job.postedAt(),
                    job.description(), job.logoUrl(), job.salaryMin(), job.salaryMax(),
                    job.salaryCurrency(), job.rawJson());
        }
    }
}