package com.smartjobtracker.service;

import com.smartjobtracker.dto.SupportDtos;
import com.smartjobtracker.model.SupportTicket;
import com.smartjobtracker.model.SupportTicketMessage;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.SupportTicketMessageRepository;
import com.smartjobtracker.repository.SupportTicketRepository;
import com.smartjobtracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/** The user side of support: open a ticket, list and read your own tickets, reply. Logs ids only, never content. */
@Service
public class SupportService {
    private static final Logger log = LoggerFactory.getLogger(SupportService.class);

    private final SupportTicketRepository tickets;
    private final SupportTicketMessageRepository messages;
    private final UserRepository users;
    private final int maxTicketsPerHour;

    public SupportService(SupportTicketRepository tickets, SupportTicketMessageRepository messages, UserRepository users,
                          @Value("${app.support.max-tickets-per-hour:10}") int maxTicketsPerHour) {
        this.tickets = tickets; this.messages = messages; this.users = users; this.maxTicketsPerHour = maxTicketsPerHour;
    }

    @Transactional
    public SupportDtos.Ticket create(Long userId, SupportDtos.CreateTicket req) {
        // Counted in the database, so the limit holds across restarts and multiple instances.
        long recent = tickets.countByUserIdAndCreatedAtAfter(userId, OffsetDateTime.now().minusHours(1));
        if (recent >= maxTicketsPerHour) {
            throw new AccountException(AccountException.Kind.TOO_MANY_REQUESTS,
                    "You've opened " + recent + " requests in the last hour. Please wait before opening another.");
        }
        SupportTicket t = new SupportTicket();
        t.setUserId(userId);
        t.setSubject(req.subject().trim());
        t.setCategory(req.category());
        t = tickets.save(t);
        addMessage(t.getId(), userId, false, req.description());
        log.info("Support ticket opened: ticketId={} userId={} category={}", t.getId(), userId, t.getCategory());
        return toDto(t, false);
    }

    @Transactional(readOnly = true)
    public List<SupportDtos.Ticket> mine(Long userId) {
        return tickets.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(t -> toSummary(t, false)).toList();
    }

    @Transactional(readOnly = true)
    public SupportDtos.Ticket mine(Long userId, Long ticketId) {
        return toDto(owned(userId, ticketId), false);
    }

    @Transactional
    public SupportDtos.Ticket reply(Long userId, Long ticketId, SupportDtos.Reply req) {
        SupportTicket t = owned(userId, ticketId);
        if ("CLOSED".equals(t.getStatus())) {
            throw new AccountException(AccountException.Kind.CONFLICT, "This request is closed. Please open a new one.");
        }
        addMessage(t.getId(), userId, false, req.body());
        if ("RESOLVED".equals(t.getStatus())) t.setStatus("OPEN"); // the user says it isn't resolved
        t.setUpdatedAt(OffsetDateTime.now());
        tickets.save(t);
        log.info("Support ticket reply from user: ticketId={} userId={}", t.getId(), userId);
        return toDto(t, false);
    }

    // ─── shared with AdminService ──────────────────────────────────────────────

    SupportTicketMessage addMessage(Long ticketId, Long authorId, boolean fromAdmin, String body) {
        SupportTicketMessage m = new SupportTicketMessage();
        m.setTicketId(ticketId);
        m.setAuthorId(authorId);
        m.setFromAdmin(fromAdmin);
        m.setBody(body.trim());
        return messages.save(m);
    }

    /** Full ticket with its conversation. Admin views include the user's and assignee's emails. */
    SupportDtos.Ticket toDto(SupportTicket t, boolean forAdmin) {
        List<SupportDtos.Message> conversation = messages.findByTicketIdOrderByCreatedAtAscIdAsc(t.getId()).stream()
                .map(m -> new SupportDtos.Message(m.getId(), m.isFromAdmin(),
                        m.isFromAdmin() ? "Support team" : (forAdmin ? "User" : "You"), m.getBody(), m.getCreatedAt()))
                .toList();
        return build(t, forAdmin, conversation);
    }

    SupportDtos.Ticket toSummary(SupportTicket t, boolean forAdmin) { return build(t, forAdmin, null); }

    private SupportDtos.Ticket build(SupportTicket t, boolean forAdmin, List<SupportDtos.Message> conversation) {
        String userEmail = forAdmin ? users.findById(t.getUserId()).map(User::getEmail).orElse(null) : null;
        String adminEmail = forAdmin && t.getAssignedAdminId() != null
                ? users.findById(t.getAssignedAdminId()).map(User::getEmail).orElse(null) : null;
        return new SupportDtos.Ticket(t.getId(), t.getSubject(), t.getCategory(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt(),
                t.getUserId(), userEmail, forAdmin ? t.getAssignedAdminId() : null, adminEmail, conversation);
    }

    private SupportTicket owned(Long userId, Long ticketId) {
        return tickets.findByIdAndUserId(ticketId, userId)
                .orElseThrow(() -> new AccountException(AccountException.Kind.NOT_FOUND, "Support request not found."));
    }
}
