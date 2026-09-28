package com.smartjobtracker.repository;

import com.smartjobtracker.model.JobSearchPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobSearchPreferenceRepository extends JpaRepository<JobSearchPreference, Long> {
    Optional<JobSearchPreference> findByUserId(Long userId);
}
