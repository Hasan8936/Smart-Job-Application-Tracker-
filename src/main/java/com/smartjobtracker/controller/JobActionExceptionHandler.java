package com.smartjobtracker.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Exception handling scoped to {@link JobActionController} only, mirroring
 * {@link GmailExceptionHandler}. Without this, JobActionService's validation
 * failures (e.g. "Job not found", "Upload a resume first...") and generation
 * failures were uncaught and surfaced as a bare 500 instead of a message the
 * frontend could show.
 */
@RestControllerAdvice(assignableTypes = JobActionController.class)
public class JobActionExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage() == null ? "bad request" : ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleNotAvailable(IllegalStateException ex) {
        return ResponseEntity.status(502)
                .body(Map.of("error", ex.getMessage() == null ? "Could not generate this document right now." : ex.getMessage()));
    }
}
