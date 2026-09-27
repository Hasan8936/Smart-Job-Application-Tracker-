package com.smartjobtracker.controller;

import com.smartjobtracker.service.UniversalResumeConflictException;
import com.smartjobtracker.service.UniversalResumeNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/** Error bodies for {@link UniversalResumeController} only, so other controllers' error handling is unchanged. */
@RestControllerAdvice(assignableTypes = UniversalResumeController.class)
public class UniversalResumeExceptionHandler {

    @ExceptionHandler(UniversalResumeNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(UniversalResumeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(UniversalResumeConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(UniversalResumeConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage() == null ? "bad request" : ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) fields.put(fe.getField(), fe.getDefaultMessage());
        Map<String, Object> body = new HashMap<>();
        body.put("error", "validation failed");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }
}
