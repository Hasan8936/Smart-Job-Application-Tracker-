package com.smartjobtracker.repository;

import com.smartjobtracker.model.DeepMatchAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DeepMatchAnalysisRepository extends JpaRepository<DeepMatchAnalysis, Long> {
    Optional<DeepMatchAnalysis> findByIdAndUserId(Long id, Long userId);
}