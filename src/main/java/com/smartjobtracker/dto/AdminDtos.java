package com.smartjobtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

/** Admin console payloads. None of these carry password hashes or OAuth/Gmail/Calendar tokens. */
public final class AdminDtos {
    private AdminDtos() {}

    public static final String FLAG_CATEGORIES = "ABUSE|BILLING|BUG|DATA_ISSUE|OTHER";
    public static final String SEVERITIES = "LOW|MEDIUM|HIGH|CRITICAL";

    public record UserSummary(Long id, String email, String name, String role, boolean suspended,
                              OffsetDateTime createdAt, OffsetDateTime lastLoginAt, long openFlags) {}

    public record ResumeRef(Long id, String fileName, OffsetDateTime uploadedAt) {}

    public record Flag(Long id, String category, String severity, String note, String status,
                       Long createdBy, OffsetDateTime createdAt, Long resolvedBy, OffsetDateTime resolvedAt) {}

    public record UserDetail(Long id, String email, String name, String role, boolean suspended, String suspendedReason,
                             boolean passwordSet, boolean gmailConnected, OffsetDateTime createdAt, OffsetDateTime lastLoginAt,
                             int applicationsCount, int resumesCount, List<ResumeRef> resumes, List<Flag> flags,
                             List<SupportDtos.Ticket> tickets) {}

    /** Only returned by the explicit, audited "view resume" action. */
    public record ResumeText(Long id, String fileName, OffsetDateTime uploadedAt, String extractedText) {}

    public record NewFlag(@NotBlank @Pattern(regexp = FLAG_CATEGORIES, message = "must be one of " + FLAG_CATEGORIES) String category,
                          @NotBlank @Pattern(regexp = SEVERITIES, message = "must be one of " + SEVERITIES) String severity,
                          @Size(max = 2000) String note) {}

    public record Suspend(@Size(max = 500) String reason) {}

    public record AuditEntry(Long id, Long adminId, String adminEmail, Long targetUserId, String action, String detail,
                             OffsetDateTime createdAt) {}
}
