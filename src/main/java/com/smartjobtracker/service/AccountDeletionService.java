package com.smartjobtracker.service;

import com.smartjobtracker.dto.AccountDtos;
import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Deletes an account and everything it owns in one transaction. Most user tables have no ON DELETE CASCADE, and
 * existing foreign-key rules are left untouched, so rows are deleted explicitly, children before parents.
 * Stored Gmail/Calendar tokens are deleted with the account and revoked at Google after the commit.
 */
@Service
public class AccountDeletionService {
    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    /** Children first. Each statement is scoped to the user by :uid (or :email for reset tokens). */
    static final List<String> DELETE_STATEMENTS = List.of(
            "delete from interview_prep_questions where session_id in (select id from interview_prep_sessions where user_id = :uid)",
            "delete from interview_prep_sessions where user_id = :uid",
            "delete from resume_versions where user_id = :uid",
            "delete from resume_tailoring_suggestions where session_id in (select id from resume_tailoring_sessions where user_id = :uid)",
            "delete from resume_tailoring_sessions where user_id = :uid",
            "delete from deep_match_analyses where user_id = :uid",
            "delete from match_analyses where user_id = :uid",
            "delete from notification_deliveries where user_id = :uid",
            "delete from notification_preferences where user_id = :uid",
            "delete from reminders where user_id = :uid",
            "delete from saved_jobs where user_id = :uid",
            "delete from generated_documents where user_id = :uid",
            "delete from interview_candidates where user_id = :uid",
            "delete from application_status_history where application_id in (select id from applications where user_id = :uid)",
            "delete from ingested_emails where user_id = :uid",
            "delete from gmail_connections where user_id = :uid",
            "delete from google_calendar_tokens where user_id = :uid",
            "delete from universal_resumes where user_id = :uid",
            "delete from candidate_profiles where user_id = :uid",
            "delete from applications where user_id = :uid",
            "delete from resumes where user_id = :uid",
            "delete from user_photos where user_id = :uid",
            "delete from support_ticket_messages where ticket_id in (select id from support_tickets where user_id = :uid)",
            "delete from support_tickets where user_id = :uid",
            "delete from user_flags where user_id = :uid",
            "delete from password_reset_tokens where user_email = :email");

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager em;
    private final GmailTokenCipher cipher;
    private final GoogleTokenRevoker revoker;

    public AccountDeletionService(UserRepository users, PasswordEncoder passwordEncoder, EntityManager em,
                                  GmailTokenCipher cipher, GoogleTokenRevoker revoker) {
        this.users = users; this.passwordEncoder = passwordEncoder; this.em = em; this.cipher = cipher; this.revoker = revoker;
    }

    @Transactional
    public void deleteAccount(Long userId, AccountDtos.DeleteAccount confirmation) {
        User user = users.findById(userId)
                .orElseThrow(() -> new AccountException(AccountException.Kind.NOT_FOUND, "Account not found."));
        verify(user, confirmation);

        List<String> googleTokens = collectGoogleTokens(userId);
        int rows = 0;
        for (String sql : DELETE_STATEMENTS) {
            var q = em.createNativeQuery(sql);
            if (sql.contains(":uid")) q.setParameter("uid", userId);
            if (sql.contains(":email")) q.setParameter("email", user.getEmail());
            rows += q.executeUpdate();
        }
        // auto_apply_log has no entity; its FK cascades on PostgreSQL when the user row goes.
        em.createNativeQuery("delete from users where id = :uid").setParameter("uid", userId).executeUpdate();
        log.info("Account deleted: userId={} ownedRows={}", userId, rows);

        if (!googleTokens.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { revoker.revokeAll(userId, googleTokens); }
            });
        }
    }

    /** Password accounts confirm with their password; Google-only accounts type their email. */
    private void verify(User user, AccountDtos.DeleteAccount c) {
        if (user.isPasswordSet()) {
            if (c == null || c.password() == null || !passwordEncoder.matches(c.password(), user.getPasswordHash())) {
                throw new AccountException(AccountException.Kind.FORBIDDEN, "Enter your current password to delete your account.");
            }
        } else if (c == null || c.confirmEmail() == null || !c.confirmEmail().trim().equalsIgnoreCase(user.getEmail())) {
            throw new AccountException(AccountException.Kind.FORBIDDEN, "Type your account email to confirm deletion.");
        }
    }

    private List<String> collectGoogleTokens(Long userId) {
        List<String> tokens = new ArrayList<>();
        try {
            for (Object t : em.createNativeQuery("select encrypted_refresh_token from gmail_connections where user_id = :uid")
                    .setParameter("uid", userId).getResultList()) {
                if (t != null) tokens.add(cipher.decrypt(t.toString()));
            }
        } catch (RuntimeException e) {
            log.info("Gmail token not readable for revoke, userId={} ({})", userId, e.getClass().getSimpleName());
        }
        for (Object t : em.createNativeQuery("select refresh_token from google_calendar_tokens where user_id = :uid")
                .setParameter("uid", userId).getResultList()) {
            if (t != null) tokens.add(t.toString());
        }
        return tokens;
    }
}
