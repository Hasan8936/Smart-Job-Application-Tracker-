package com.smartjobtracker.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice(assignableTypes = InterviewPrepController.class)
public class InterviewPrepExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage() == null ? "bad request" : ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleFailure(IllegalStateException ex) {
        String detail = ex.getMessage() == null ? "Could not complete this request." : ex.getMessage();
        if (ex.getCause() != null) {
            detail += " Caused by: " + ex.getCause().getClass().getSimpleName() + (ex.getCause().getMessage() == null ? "" : ": " + ex.getCause().getMessage());
        }
        return ResponseEntity.status(502).body(Map.of("error", detail));
    }
}
