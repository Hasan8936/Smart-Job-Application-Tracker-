package com.smartjobtracker.controller;

import com.smartjobtracker.service.GeminiApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Exception handling scoped to {@link ResumeMatchController} only ({@code assignableTypes}).
 *
 * <p>Mirrors {@link ProfileExceptionHandler}'s pattern: no global {@code @ControllerAdvice},
 * so this gives {@code /api/resume/deep-match} readable JSON error bodies without changing
 * how any other controller reports errors.
 */
@RestControllerAdvice(assignableTypes = ResumeMatchController.class)
public class ResumeMatchExceptionHandler {

    @ExceptionHandler(GeminiApiException.class)
    public ResponseEntity<Map<String, Object>> handleGemini(GeminiApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage() == null ? "bad request" : ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleParseFailure(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "The AI response could not be understood. Please try again."));
    }
}
