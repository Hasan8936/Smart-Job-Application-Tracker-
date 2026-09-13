package com.smartjobtracker.dto;

import com.smartjobtracker.model.ReminderType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public class ReminderCreateRequest {
    private Long applicationId;
    @NotNull
    private OffsetDateTime remindAt;
    @NotNull
    private ReminderType type;
    @Size(max = 1024)
    private String message;

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public OffsetDateTime getRemindAt() { return remindAt; }
    public void setRemindAt(OffsetDateTime remindAt) { this.remindAt = remindAt; }
    public ReminderType getType() { return type; }
    public void setType(ReminderType type) { this.type = type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
