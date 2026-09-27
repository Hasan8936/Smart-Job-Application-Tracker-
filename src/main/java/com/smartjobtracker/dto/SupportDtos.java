package com.smartjobtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public final class SupportDtos {
    private SupportDtos() {}

    public static final String CATEGORIES = "ACCOUNT|BUG|JOB_SEARCH|RESUME|BILLING|OTHER";
    public static final String STATUSES = "OPEN|IN_PROGRESS|RESOLVED|CLOSED";

    public record CreateTicket(@NotBlank @Size(max = 200) String subject,
                               @NotBlank @Size(max = 5000) String description,
                               @NotBlank @Pattern(regexp = CATEGORIES, message = "must be one of " + CATEGORIES) String category) {}

    public record Reply(@NotBlank @Size(max = 5000) String body) {}

    /** Admin ticket update: any of status / assignment. {@code assigneeId} null with assign=true means "assign to me". */
    public record AdminUpdate(@Pattern(regexp = STATUSES, message = "must be one of " + STATUSES) String status,
                              Boolean assign, Long assigneeId, Boolean unassign) {}

    public record Message(Long id, boolean fromAdmin, String author, String body, OffsetDateTime createdAt) {}

    /** {@code userEmail} and {@code assignedAdminEmail} are only filled for admins. */
    public record Ticket(Long id, String subject, String category, String status, OffsetDateTime createdAt,
                         OffsetDateTime updatedAt, Long userId, String userEmail, Long assignedAdminId,
                         String assignedAdminEmail, List<Message> messages) {}
}
