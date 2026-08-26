package com.smartjobtracker.dto;

import jakarta.validation.constraints.Pattern;

public class NotificationVerificationDto {
    @Pattern(regexp = "[0-9]{6}", message = "code must be six digits")
    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}