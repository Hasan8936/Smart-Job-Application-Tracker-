package com.smartjobtracker.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Exception handling scoped to {@link GmailController} only ({@code assignableTypes}),
 * following the same pattern as {@link ProfileExceptionHandler}: no global
 * {@code @ControllerAdvice}, so this only changes error reporting for {@code /api/gmail/**}.
 *
 * <p>Note: {@code /api/gmail/callback} returns a redirect and handles its own errors by
 * redirecting with {@code ?gmail=error} rather than throwing, so it never reaches this advice.
 */
@RestControllerAdvice(assignableTypes = GmailController.class)
public class GmailExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleNotConfigured(IllegalStateException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage() == null ? "Gmail integration is not available." : ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage() == null ? "bad request" : ex.getMessage()));
    }
}
