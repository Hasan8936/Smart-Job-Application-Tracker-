package com.smartjobtracker.service;

import org.springframework.http.HttpStatus;

/**
 * Raised for any failure calling the Anthropic API (bad/missing key, rate limiting,
 * upstream errors, timeouts, or connection failures). Carries the HTTP status the
 * API layer should report to the client, and a message that is safe to show as-is
 * (never includes the API key or raw upstream payloads).
 */
public class AnthropicApiException extends RuntimeException {

    private final HttpStatus status;

    public AnthropicApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
