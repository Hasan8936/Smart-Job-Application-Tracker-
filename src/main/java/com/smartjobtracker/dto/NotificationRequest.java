package com.smartjobtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NotificationRequest {
    @NotBlank @Size(max = 150) private String eventKey;
    @NotBlank @Size(max = 4096) private String message;
    @Size(max = 50) private String eventType;

    public String getEventKey() { return eventKey; }
    public void setEventKey(String value) { eventKey = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
    public String getEventType() { return eventType; }
    public void setEventType(String value) { eventType = value; }
}