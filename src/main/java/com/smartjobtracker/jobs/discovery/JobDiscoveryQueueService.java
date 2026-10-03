package com.smartjobtracker.jobs.discovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartjobtracker.jobs.provider.JobProvider.JobQuery;
import com.smartjobtracker.model.JobDiscoveryTask;
import com.smartjobtracker.repository.JobDiscoveryTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class JobDiscoveryQueueService {
    private final JobDiscoveryTaskRepository tasks;
    private final ObjectMapper mapper;
    private final int maxAttempts;
    private final Duration retryBase;

    public JobDiscoveryQueueService(JobDiscoveryTaskRepository tasks, ObjectMapper mapper,
                                    @Value("${app.job-discovery.queue.max-attempts:3}") int maxAttempts,
                                    @Value("${app.job-discovery.queue.retry-base-ms:5000}") long retryBaseMs) {
        this.tasks = tasks;
        this.mapper = mapper;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBase = Duration.ofMillis(Math.max(1000L, retryBaseMs));
    }

    @Transactional
    public void enqueue(String syncId, JobQuery query) {
        JobDiscoveryTask task = new JobDiscoveryTask();
        OffsetDateTime now = OffsetDateTime.now();
        task.setId(syncId);
        try {
            task.setQueryJson(mapper.writeValueAsString(query));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize discovery request", e);
        }
        task.setStatus("QUEUED");
        task.setAttempts(0);
        task.setAvailableAt(now);
        task.setCreatedAt(now);
        tasks.save(task);
    }

    @Transactional(readOnly = true)
    public Optional<JobDiscoveryTask> find(String syncId) {
        return tasks.findById(syncId);
    }

    @Transactional
    public Optional<JobDiscoveryTask> claimNext() {
        Optional<JobDiscoveryTask> next = tasks.findFirstByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
                "QUEUED", OffsetDateTime.now());
        if (next.isEmpty()) return Optional.empty();
        JobDiscoveryTask task = next.get();
        task.setStatus("RUNNING");
        task.setStartedAt(OffsetDateTime.now());
        task.setAttempts(task.getAttempts() + 1);
        task.setErrorMessage(null);
        return Optional.of(tasks.save(task));
    }

    public JobQuery query(JobDiscoveryTask task) {
        try {
            return mapper.readValue(task.getQueryJson(), JobQuery.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid queued discovery request", e);
        }
    }

    @Transactional
    public void complete(JobDiscoveryTask task, int totalSaved) {
        task.setStatus("DONE");
        task.setTotalSaved(totalSaved);
        task.setFinishedAt(OffsetDateTime.now());
        task.setErrorMessage(null);
        tasks.save(task);
    }

    @Transactional
    public boolean retryOrFail(JobDiscoveryTask task, String error) {
        task.setErrorMessage(trim(error));
        if (!isRetryable(error) || task.getAttempts() >= maxAttempts) {
            task.setStatus("FAILED");
            task.setFinishedAt(OffsetDateTime.now());
            tasks.save(task);
            return false;
        }
        long multiplier = 1L << Math.min(task.getAttempts() - 1, 10);
        task.setStatus("QUEUED");
        task.setAvailableAt(OffsetDateTime.now().plus(retryBase.multipliedBy(multiplier)));
        tasks.save(task);
        return true;
    }

    private boolean isRetryable(String error) {
        if (error == null) return true;
        return !error.startsWith("No job source is enabled")
                && !error.startsWith("Invalid queued discovery request")
                && !error.startsWith("Could not serialize discovery request");
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) return "discovery task failed";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
