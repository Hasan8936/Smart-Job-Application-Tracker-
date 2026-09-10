package com.smartjobtracker.repository;
import com.smartjobtracker.model.InterviewPrepQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InterviewPrepQuestionRepository extends JpaRepository<InterviewPrepQuestion, Long> {
    List<InterviewPrepQuestion> findBySessionIdOrderByPositionAsc(Long sessionId);
}
