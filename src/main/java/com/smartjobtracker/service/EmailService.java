package com.smartjobtracker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) { this.mailSender = mailSender; }

    public void sendPasswordResetEmail(String toEmail, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!fromAddress.isBlank()) message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Reset your Smart Job Tracker password");
        message.setText("We received a request to reset your password.\n\n"
                + "Set a new password within 30 minutes:\n"
                + frontendUrl + "/reset-password?token=" + rawToken + "\n\n"
                + "If you did not request this, you can safely ignore this email.");
        mailSender.send(message);
    }
}