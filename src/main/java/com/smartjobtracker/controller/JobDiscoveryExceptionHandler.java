package com.smartjobtracker.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Exception handling scoped to {@link JobDiscoveryController} only ({@code assignableTypes}),
 * following the same pattern as {@link ProfileExceptionHandler} and
 * {@link ResumeMatchExceptionHandler}: no global {@code @ControllerAdvice}, so this only
 * changes error reporting for {@code /api/jobs/**}.
 */
@RestControllerAdvice(assignableTypes = JobDiscoveryController.class)
public class JobDiscoveryExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleNoProviderEnabled(IllegalStateException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage() == null ? "job discovery failed" : ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage() == null ? "bad request" : ex.getMessage()));
    }
}
