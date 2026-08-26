package com.smartjobtracker.repository;

import com.smartjobtracker.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByUserId(Long userId);
    Optional<JobApplication> findFirstByUserIdAndCompanyNameIgnoreCaseAndRoleTitleIgnoreCase(Long userId, String companyName, String roleTitle);
}
