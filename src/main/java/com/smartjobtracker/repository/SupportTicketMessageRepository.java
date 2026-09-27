package com.smartjobtracker.repository;

import com.smartjobtracker.model.SupportTicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, Long> {
    List<SupportTicketMessage> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);
}
