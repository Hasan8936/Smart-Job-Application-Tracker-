package com.smartjobtracker.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "interview_candidates", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "calendar_event_id"}))
public class InterviewCandidate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false) private String source = "CALENDAR";
    @Column(name = "calendar_event_id", nullable = false) private String calendarEventId;
    private String title;
    @Column(columnDefinition = "text") private String description;
    @Column(name = "event_start") private OffsetDateTime eventStart;
    @Column(name = "event_end") private OffsetDateTime eventEnd;
    @Column(name = "suggested_application_id") private Long suggestedApplicationId;
    @Column(name = "match_method") private String matchMethod;
    @Column(nullable = false) private String status = "PENDING";
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "reviewed_at") private OffsetDateTime reviewedAt;

    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getUserId() { return userId; } public void setUserId(Long v) { userId = v; }
    public String getSource() { return source; } public void setSource(String v) { source = v; }
    public String getCalendarEventId() { return calendarEventId; } public void setCalendarEventId(String v) { calendarEventId = v; }
    public String getTitle() { return title; } public void setTitle(String v) { title = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public OffsetDateTime getEventStart() { return eventStart; } public void setEventStart(OffsetDateTime v) { eventStart = v; }
    public OffsetDateTime getEventEnd() { return eventEnd; } public void setEventEnd(OffsetDateTime v) { eventEnd = v; }
    public Long getSuggestedApplicationId() { return suggestedApplicationId; } public void setSuggestedApplicationId(Long v) { suggestedApplicationId = v; }
    public String getMatchMethod() { return matchMethod; } public void setMatchMethod(String v) { matchMethod = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; } public void setReviewedAt(OffsetDateTime v) { reviewedAt = v; }
}
