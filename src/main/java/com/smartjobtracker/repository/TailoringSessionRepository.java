package com.smartjobtracker.repository;

import com.smartjobtracker.model.TailoringSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TailoringSessionRepository extends JpaRepository<TailoringSession, Long> {
    Optional<TailoringSession> findByIdAndUserId(Long id, Long userId);
}