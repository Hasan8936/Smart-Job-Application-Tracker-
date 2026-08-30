package com.smartjobtracker.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Exception handling scoped to {@link InterviewController} only ({@code assignableTypes}),
 * following the same pattern as {@link GmailExceptionHandler}, {@link ResumeMatchExceptionHandler},
 * and {@link JobDiscoveryExceptionHandler}: no global {@code @ControllerAdvice}.
 */
@RestControllerAdvice(assignableTypes = InterviewController.class)
public class InterviewExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleNotConfiguredOrScopeMissing(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage() == null ? "Interview detection is not available." : ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage() == null ? "bad request" : ex.getMessage()));
    }
}
