package com.smartjobtracker.service;

import com.smartjobtracker.model.User;
import com.smartjobtracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Promotes the account whose email equals {@code SUPER_ADMIN_EMAIL} to ADMIN — at startup, and at login for an
 * account created after startup. Never creates an account or handles a password: the admin signs in with their
 * normal credentials. Unset → no admin. Idempotent.
 */
@Component
public class SuperAdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final UserRepository users;
    private final String superAdminEmail;

    public SuperAdminBootstrap(UserRepository users, @Value("${app.super-admin-email:}") String superAdminEmail) {
        this.users = users;
        this.superAdminEmail = superAdminEmail == null ? "" : superAdminEmail.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (superAdminEmail.isEmpty()) {
            log.info("SUPER_ADMIN_EMAIL not set; no super admin configured");
            return;
        }
        users.findByEmailIgnoreCase(superAdminEmail).ifPresentOrElse(
                u -> { if (promoteIfConfigured(u)) users.save(u); },
                () -> log.info("SUPER_ADMIN_EMAIL is set but no account with that email exists yet; it will be promoted on first login"));
    }

    /** Sets ADMIN on {@code user} when it is the configured super admin; returns whether it changed. Caller saves. */
    public boolean promoteIfConfigured(User user) {
        if (superAdminEmail.isEmpty() || user == null || user.getEmail() == null) return false;
        if (!user.getEmail().equalsIgnoreCase(superAdminEmail) || user.isAdmin()) return false;
        user.setRole("ADMIN");
        log.info("Promoted userId={} to ADMIN (SUPER_ADMIN_EMAIL)", user.getId());
        return true;
    }
}
