package com.smartjobtracker.repository;
import com.smartjobtracker.model.SavedJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SavedJobRepository extends JpaRepository<SavedJob, Long> { Optional<SavedJob> findByUserIdAndJobPostingId(Long userId, Long jobPostingId); }