package com.smartjobtracker.service;

import com.smartjobtracker.dto.AdminDtos;
import com.smartjobtracker.dto.SupportDtos;
import com.smartjobtracker.model.*;
import com.smartjobtracker.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Super-admin console. Every method requires ROLE_ADMIN (in addition to the /api/admin/** URL rule) and every action
 * is written to admin_audit_log. Responses never contain password hashes or OAuth/Gmail/Calendar tokens; resume text
 * is only returned by the explicit, audited {@link #viewResume}.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminService {
    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository users;
    private final UserFlagRepository flags;
    private final SupportTicketRepository tickets;
    private final ResumeRepository resumes;
    private final JobApplicationRepository applications;
    private final GmailConnectionRepository gmail;
    private final AdminAuditLogRepository audit;
    private final SupportService support;
    private final EmailService email;
    private final String frontendUrl;

    public AdminService(UserRepository users, UserFlagRepository flags, SupportTicketRepository tickets, ResumeRepository resumes,
                        JobApplicationRepository applications, GmailConnectionRepository gmail, AdminAuditLogRepository audit,
                        SupportService support, EmailService email, @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.users = users; this.flags = flags; this.tickets = tickets; this.resumes = resumes; this.applications = applications;
        this.gmail = gmail; this.audit = audit; this.support = support; this.email = email; this.frontendUrl = frontendUrl;
    }

    // ─── Users ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminDtos.UserSummary> searchUsers(String q, OffsetDateTime from, OffsetDateTime to, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return users.adminSearch(q == null || q.isBlank() ? null : q.trim(), from, to, pageable)
                .map(u -> new AdminDtos.UserSummary(u.getId(), u.getEmail(), u.getName(), u.getRole(), u.isSuspended(),
                        u.getCreatedAt(), u.getLastLoginAt(), flags.countByUserIdAndStatus(u.getId(), "OPEN")));
    }

    @Transactional
    public AdminDtos.UserDetail userDetail(Long adminId, Long userId) {
        User u = user(userId);
        List<Resume> userResumes = resumes.findByUserIdOrderByUploadedAtDescIdDesc(userId);
        record(adminId, userId, "VIEW_USER", null);
        return new AdminDtos.UserDetail(u.getId(), u.getEmail(), u.getName(), u.getRole(), u.isSuspended(), u.getSuspendedReason(),
                u.isPasswordSet(), gmail.findByUserId(userId).map(c -> "CONNECTED".equalsIgnoreCase(c.getStatus())).orElse(false),
                u.getCreatedAt(), u.getLastLoginAt(), applications.findByUserId(userId).size(), userResumes.size(),
                userResumes.stream().map(r -> new AdminDtos.ResumeRef(r.getId(), r.getFileName(), r.getUploadedAt())).toList(),
                flags.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::flagDto).toList(),
                tickets.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(t -> support.toSummary(t, true)).toList());
    }

    /** The only way an admin sees resume text; always audited. */
    @Transactional
    public AdminDtos.ResumeText viewResume(Long adminId, Long userId, Long resumeId) {
        Resume r = resumes.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new AccountException(AccountException.Kind.NOT_FOUND, "Resume not found for this user."));
        record(adminId, userId, "VIEW_RESUME", "resumeId=" + resumeId);
        return new AdminDtos.ResumeText(r.getId(), r.getFileName(), r.getUploadedAt(), r.getExtractedText());
    }

    // ─── Flags & suspension ─────────────────────────────────────────────────────

    @Transactional
    public AdminDtos.Flag addFlag(Long adminId, Long userId, AdminDtos.NewFlag req) {
        user(userId);
        UserFlag f = new UserFlag();
        f.setUserId(userId); f.setCategory(req.category()); f.setSeverity(req.severity());
        f.setNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        f.setCreatedBy(adminId);
        f = flags.save(f);
        record(adminId, userId, "FLAG_ADD", "flagId=" + f.getId() + " category=" + f.getCategory() + " severity=" + f.getSeverity());
        return flagDto(f);
    }

    @Transactional
    public AdminDtos.Flag resolveFlag(Long adminId, Long flagId) {
        UserFlag f = flags.findById(flagId).orElseThrow(() -> new AccountException(AccountException.Kind.NOT_FOUND, "Flag not found."));
        if (!"RESOLVED".equals(f.getStatus())) {
            f.setStatus("RESOLVED"); f.setResolvedBy(adminId); f.setResolvedAt(OffsetDateTime.now());
            f = flags.save(f);
            record(adminId, f.getUserId(), "FLAG_RESOLVE", "flagId=" + f.getId());
        }
        return flagDto(f);
    }

    @Transactional
    public AdminDtos.UserSummary setSuspended(Long adminId, Long userId, boolean suspend, String reason) {
        User u = user(userId);
        if (suspend && (userId.equals(adminId) || u.isAdmin())) {
            throw new AccountException(AccountException.Kind.CONFLICT, "Admins can't be suspended from the console.");
        }
        u.setSuspended(suspend);
        u.setSuspendedReason(suspend && reason != null && !reason.isBlank() ? reason.trim() : null);
        users.save(u);
        record(adminId, userId, suspend ? "SUSPEND" : "UNSUSPEND", null);
        return new AdminDtos.UserSummary(u.getId(), u.getEmail(), u.getName(), u.getRole(), u.isSuspended(), u.getCreatedAt(),
                u.getLastLoginAt(), flags.countByUserIdAndStatus(userId, "OPEN"));
    }

    // ─── Tickets ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<SupportDtos.Ticket> tickets(String status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<SupportTicket> result = status == null || status.isBlank() ? tickets.findAll(pageable) : tickets.findByStatus(status, pageable);
        return result.map(t -> support.toSummary(t, true));
    }

    @Transactional(readOnly = true)
    public SupportDtos.Ticket ticket(Long ticketId) { return support.toDto(ticketEntity(ticketId), true); }

    @Transactional
    public SupportDtos.Ticket reply(Long adminId, Long ticketId, SupportDtos.Reply req) {
        SupportTicket t = ticketEntity(ticketId);
        support.addMessage(t.getId(), adminId, true, req.body());
        if ("OPEN".equals(t.getStatus())) t.setStatus("IN_PROGRESS");
        if (t.getAssignedAdminId() == null) t.setAssignedAdminId(adminId);
        t.setUpdatedAt(OffsetDateTime.now());
        tickets.save(t);
        record(adminId, t.getUserId(), "TICKET_REPLY", "ticketId=" + t.getId());
        notifyUser(t, req.body());
        return support.toDto(t, true);
    }

    @Transactional
    public SupportDtos.Ticket update(Long adminId, Long ticketId, SupportDtos.AdminUpdate req) {
        SupportTicket t = ticketEntity(ticketId);
        if (req.status() != null && !req.status().equals(t.getStatus())) {
            String from = t.getStatus();
            t.setStatus(req.status());
            record(adminId, t.getUserId(), "TICKET_STATUS", "ticketId=" + t.getId() + " " + from + "->" + req.status());
        }
        if (Boolean.TRUE.equals(req.unassign())) {
            t.setAssignedAdminId(null);
            record(adminId, t.getUserId(), "TICKET_ASSIGN", "ticketId=" + t.getId() + " unassigned");
        } else if (Boolean.TRUE.equals(req.assign())) {
            Long assignee = req.assigneeId() == null ? adminId : req.assigneeId();
            User a = user(assignee);
            if (!a.isAdmin()) throw new AccountException(AccountException.Kind.BAD_REQUEST, "Tickets can only be assigned to admins.");
            t.setAssignedAdminId(assignee);
            record(adminId, t.getUserId(), "TICKET_ASSIGN", "ticketId=" + t.getId() + " assignee=" + assignee);
        }
        t.setUpdatedAt(OffsetDateTime.now());
        tickets.save(t);
        return support.toDto(t, true);
    }

    // ─── Audit ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminDtos.AuditEntry> auditLog(int page, int size) {
        return audit.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)))
                .map(a -> new AdminDtos.AuditEntry(a.getId(), a.getAdminId(),
                        a.getAdminId() == null ? null : users.findById(a.getAdminId()).map(User::getEmail).orElse(null),
                        a.getTargetUserId(), a.getAction(), a.getDetail(), a.getCreatedAt()));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private void record(Long adminId, Long targetUserId, String action, String detail) {
        AdminAuditLog entry = new AdminAuditLog();
        entry.setAdminId(adminId); entry.setTargetUserId(targetUserId); entry.setAction(action); entry.setDetail(detail);
        audit.save(entry);
        log.info("Admin action: adminId={} action={} targetUserId={}", adminId, action, targetUserId);
    }

    /** Best-effort email; a mail failure never undoes the reply. The reply text goes to the user, not to logs. */
    private void notifyUser(SupportTicket t, String replyBody) {
        users.findById(t.getUserId()).ifPresent(u -> {
            try {
                email.sendReminderEmail(u.getEmail(), "Update on your support request #" + t.getId() + ": " + t.getSubject(),
                        "Hi" + (u.getName() == null ? "" : " " + u.getName()) + ",\n\nOur support team replied to your request:\n\n"
                                + replyBody.trim() + "\n\nView or reply: " + frontendUrl + "/support\n\n— Smart Job Tracker");
            } catch (RuntimeException e) {
                log.warn("Support reply email not sent for ticketId={} ({})", t.getId(), e.getClass().getSimpleName());
            }
        });
    }

    private AdminDtos.Flag flagDto(UserFlag f) {
        return new AdminDtos.Flag(f.getId(), f.getCategory(), f.getSeverity(), f.getNote(), f.getStatus(), f.getCreatedBy(),
                f.getCreatedAt(), f.getResolvedBy(), f.getResolvedAt());
    }

    private User user(Long id) {
        return users.findById(id).orElseThrow(() -> new AccountException(AccountException.Kind.NOT_FOUND, "User not found."));
    }

    private SupportTicket ticketEntity(Long id) {
        return tickets.findById(id).orElseThrow(() -> new AccountException(AccountException.Kind.NOT_FOUND, "Support request not found."));
    }
}
