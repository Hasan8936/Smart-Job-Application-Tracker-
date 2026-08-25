package com.smartjobtracker.repository;

import com.smartjobtracker.model.MatchAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MatchAnalysisRepository extends JpaRepository<MatchAnalysis, Long> {
    Optional<MatchAnalysis> findByUserIdAndResumeIdAndSourceHash(Long userId, Long resumeId, String sourceHash);
}