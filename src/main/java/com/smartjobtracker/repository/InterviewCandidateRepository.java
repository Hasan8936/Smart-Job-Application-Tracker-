package com.smartjobtracker.repository;

import com.smartjobtracker.model.InterviewCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewCandidateRepository extends JpaRepository<InterviewCandidate, Long> {
    Optional<InterviewCandidate> findByUserIdAndCalendarEventId(Long userId, String calendarEventId);
    List<InterviewCandidate> findByUserIdAndStatusOrderByEventStartAsc(Long userId, String status);
    Optional<InterviewCandidate> findByIdAndUserId(Long id, Long userId);
}
