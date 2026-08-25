package com.smartjobtracker.jobs.discovery;

import com.smartjobtracker.model.JobPosting;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JobDeduplicator {
    public List<JobPosting> deduplicate(List<JobPosting> jobs) {
        Map<String, JobPosting> unique = new LinkedHashMap<>();
        for (JobPosting job : jobs) unique.putIfAbsent(job.getDedupeHash(), job);
        return unique.values().stream().toList();
    }
}