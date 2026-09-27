package com.smartjobtracker.controller;

import com.smartjobtracker.service.AccountException;
import com.smartjobtracker.service.ProfilePhotoProcessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

/** Error bodies for the account, support and admin controllers only (no global handler exists). */
@RestControllerAdvice(assignableTypes = {AccountController.class, SupportController.class, AdminController.class})
public class AccountExceptionHandler {

    @ExceptionHandler(AccountException.class)
    public ResponseEntity<Map<String, Object>> account(AccountException ex) {
        HttpStatus status = switch (ex.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
        };
        return ResponseEntity.status(status).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ProfilePhotoProcessor.InvalidPhotoException.class)
    public ResponseEntity<Map<String, Object>> photo(ProfilePhotoProcessor.InvalidPhotoException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", "Photos must be 2 MB or smaller."));
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
