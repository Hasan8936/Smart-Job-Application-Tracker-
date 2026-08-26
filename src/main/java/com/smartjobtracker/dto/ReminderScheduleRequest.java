package com.smartjobtracker.dto;

import com.smartjobtracker.model.ReminderType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public class ReminderScheduleRequest {
    @NotNull
    private ReminderType type;
    @NotNull
    private LocalDateTime eventAt;
    @Size(max = 100)
    private String timezone;
    private Long applicationId;
    @Size(max = 150)
    private String eventKey;
    @Size(max = 1024)
    private String message;

    public ReminderType getType() { return type; }
    public void setType(ReminderType type) { this.type = type; }
    public LocalDateTime getEventAt() { return eventAt; }
    public void setEventAt(LocalDateTime eventAt) { this.eventAt = eventAt; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}