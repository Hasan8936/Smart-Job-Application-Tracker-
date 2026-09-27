package com.smartjobtracker.repository;

import com.smartjobtracker.model.UserFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserFlagRepository extends JpaRepository<UserFlag, Long> {
    List<UserFlag> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndStatus(Long userId, String status);
}
