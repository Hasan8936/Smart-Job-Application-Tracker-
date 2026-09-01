package com.smartjobtracker.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Exception handling scoped to {@link ResumeTailoringController} only, mirroring
 * {@link GmailExceptionHandler}. Without this, failures (including PDF rendering
 * failures) surfaced as a bare 500 with no detail. Includes the cause's message
 * for IllegalStateException specifically since renderPdf() wraps the real
 * PDFBox/AWT exception that way -- this is a debugging aid for the app owner,
 * not a general practice for production APIs.
 */
@RestControllerAdvice(assignableTypes = ResumeTailoringController.class)
public class ResumeTailoringExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage() == null ? "bad request" : ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleFailure(IllegalStateException ex) {
        String detail = ex.getMessage() == null ? "Could not complete this request." : ex.getMessage();
        if (ex.getCause() != null) {
            detail += " Caused by: " + ex.getCause().getClass().getSimpleName()
                    + (ex.getCause().getMessage() == null ? "" : ": " + ex.getCause().getMessage());
        }
        return ResponseEntity.status(502).body(Map.of("error", detail));
    }
}
