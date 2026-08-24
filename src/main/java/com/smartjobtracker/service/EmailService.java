package com.smartjobtracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // Sender address. Defaults to the SMTP username (correct for Gmail); override with
    // MAIL_FROM for providers where the verified sender differs from the login user.
    @Value("${MAIL_FROM:${spring.mail.username:}}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) { this.mailSender = mailSender; }

    public void sendPasswordResetEmail(String toEmail, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromAddress != null && !fromAddress.isBlank()) message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Reset your Smart Job Tracker password");
        message.setText("We received a request to reset your password.\n\n"
                + "Set a new password within 30 minutes:\n"
                + frontendUrl + "/reset-password?token=" + rawToken + "\n\n"
                + "If you did not request this, you can safely ignore this email.");
        try {
            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (MailException ex) {
            // Surface the real SMTP cause (bad credentials, host unreachable, TLS, ...) in the
            // Render logs so misconfiguration is diagnosable, then rethrow so the API reports failure.
            log.error("Failed to send password reset email to {} — check MAIL_* env vars. Cause: {}",
                    toEmail, ex.getMessage(), ex);
            throw ex;
        }
    }
}