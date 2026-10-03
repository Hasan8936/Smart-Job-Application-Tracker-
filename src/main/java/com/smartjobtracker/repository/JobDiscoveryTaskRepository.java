package com.smartjobtracker.repository;

import com.smartjobtracker.model.JobDiscoveryTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface JobDiscoveryTaskRepository extends JpaRepository<JobDiscoveryTask, String> {
    Optional<JobDiscoveryTask> findFirstByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            String status, OffsetDateTime availableAt);
}
