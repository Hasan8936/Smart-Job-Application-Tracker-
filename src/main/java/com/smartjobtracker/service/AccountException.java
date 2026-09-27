package com.smartjobtracker.service;

/** Account/support/admin request errors with a user-safe message; mapped to HTTP status by the scoped handlers. */
public class AccountException extends RuntimeException {
    public enum Kind { BAD_REQUEST, FORBIDDEN, NOT_FOUND, CONFLICT, TOO_MANY_REQUESTS }

    private final Kind kind;

    public AccountException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
}
