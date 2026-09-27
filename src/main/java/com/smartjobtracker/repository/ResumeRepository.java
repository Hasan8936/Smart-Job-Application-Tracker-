package com.smartjobtracker.repository;

import com.smartjobtracker.model.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    List<Resume> findByUserId(Long userId);
    java.util.Optional<Resume> findByIdAndUserId(Long id, Long userId);
    /** Newest resume first (ties by id), for picking a default when no universal resume exists. */
    List<Resume> findByUserIdOrderByUploadedAtDescIdDesc(Long userId);
}
