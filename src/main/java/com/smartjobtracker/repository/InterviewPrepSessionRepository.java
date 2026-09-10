package com.smartjobtracker.repository;
import com.smartjobtracker.model.InterviewPrepSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InterviewPrepSessionRepository extends JpaRepository<InterviewPrepSession, Long> {
    Optional<InterviewPrepSession> findByIdAndUserId(Long id, Long userId);
    List<InterviewPrepSession> findByUserIdOrderByCreatedAtDesc(Long userId);
}
