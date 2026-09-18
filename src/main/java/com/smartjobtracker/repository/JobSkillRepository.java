package com.smartjobtracker.repository;

import com.smartjobtracker.model.JobSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobSkillRepository extends JpaRepository<JobSkill, Long> {
    List<JobSkill> findByJobPostingIdOrderByName(Long jobPostingId);
    List<JobSkill> findByJobPostingIdIn(java.util.Collection<Long> jobPostingIds);
    void deleteByJobPostingId(Long jobPostingId);
}