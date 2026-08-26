package com.smartjobtracker.service;

public interface NotificationProvider {
    String channel();
    Submission send(String recipient, String message);
    record Submission(String providerMessageId) {}
}