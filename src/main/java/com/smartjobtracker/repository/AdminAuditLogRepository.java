package com.smartjobtracker.repository;

import com.smartjobtracker.model.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    Page<AdminAuditLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
    List<AdminAuditLog> findByTargetUserIdOrderByCreatedAtDesc(Long targetUserId);
}
