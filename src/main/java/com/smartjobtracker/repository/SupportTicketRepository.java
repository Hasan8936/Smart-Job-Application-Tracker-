package com.smartjobtracker.repository;

import com.smartjobtracker.model.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<SupportTicket> findByIdAndUserId(Long id, Long userId);
    Page<SupportTicket> findByStatus(String status, Pageable pageable);
    /** For the per-user creation rate limit. */
    long countByUserIdAndCreatedAtAfter(Long userId, OffsetDateTime since);
}
