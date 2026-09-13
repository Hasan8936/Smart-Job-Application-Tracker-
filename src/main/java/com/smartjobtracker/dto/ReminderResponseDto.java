package com.smartjobtracker.dto;

import com.smartjobtracker.model.Reminder;
import com.smartjobtracker.model.ReminderType;
import java.time.OffsetDateTime;

public class ReminderResponseDto {
    private Long id;
    private Long applicationId;
    private OffsetDateTime remindAt;
    private ReminderType type;
    private String message;

    public static ReminderResponseDto from(Reminder r) {
        ReminderResponseDto dto = new ReminderResponseDto();
        dto.id = r.getId();
        dto.applicationId = r.getApplicationId();
        dto.remindAt = r.getRemindAt();
        dto.type = r.getType();
        dto.message = r.getMessage();
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public OffsetDateTime getRemindAt() { return remindAt; }
    public void setRemindAt(OffsetDateTime remindAt) { this.remindAt = remindAt; }
    public ReminderType getType() { return type; }
    public void setType(ReminderType type) { this.type = type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
