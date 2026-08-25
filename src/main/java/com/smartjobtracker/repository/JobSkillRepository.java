package com.smartjobtracker.repository;

import com.smartjobtracker.model.JobSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobSkillRepository extends JpaRepository<JobSkill, Long> {
    List<JobSkill> findByJobPostingIdOrderByName(Long jobPostingId);
    void deleteByJobPostingId(Long jobPostingId);
}