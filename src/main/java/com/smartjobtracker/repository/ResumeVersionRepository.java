package com.smartjobtracker.repository;

import com.smartjobtracker.model.ResumeVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, Long> {
    List<ResumeVersion> findByUserIdOrderByCreatedAtDesc(Long userId);
}