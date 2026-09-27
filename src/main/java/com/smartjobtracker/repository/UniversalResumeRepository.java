package com.smartjobtracker.repository;

import com.smartjobtracker.model.UniversalResume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UniversalResumeRepository extends JpaRepository<UniversalResume, Long> {
    Optional<UniversalResume> findByUserId(Long userId);
}
