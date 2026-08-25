package com.smartjobtracker.service;

/**
 * Thrown when a candidate profile (or the resume needed to build one) does not
 * exist for the current user. Mapped to HTTP 404 by the profile-scoped handler.
 * A foreign/nonexistent resume is reported as not-found on purpose, so resume
 * existence is never leaked across users.
 */
public class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException(String message) {
        super(message);
    }
}
