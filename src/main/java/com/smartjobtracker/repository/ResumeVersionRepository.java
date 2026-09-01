package com.smartjobtracker.repository;

import com.smartjobtracker.model.ResumeVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, Long> {
    List<ResumeVersion> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<ResumeVersion> findByIdAndUserId(Long id, Long userId);
}