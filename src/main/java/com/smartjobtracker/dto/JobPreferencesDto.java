package com.smartjobtracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Job preferences from the onboarding popup. {@code status} and {@code updatedAt} are response-only (ignored on PUT).
 * Codes: experienceLevel FRESHER | JUNIOR (1-3 yrs) | MID (3-5 yrs) | SENIOR (5+ yrs);
 * workModes ONSITE | HYBRID | REMOTE; jobTypes FULL_TIME | PART_TIME | INTERNSHIP | CONTRACT.
 */
public record JobPreferencesDto(
        String status,
        @NotNull(message = "add at least one role")
        @Size(min = 1, max = 5, message = "choose 1 to 5 roles")
        List<@NotBlank @Size(max = 80) String> roles,
        @Pattern(regexp = "FRESHER|JUNIOR|MID|SENIOR", message = "unknown experience level")
        String experienceLevel,
        @Size(max = 6, message = "choose up to 6 locations")
        List<@NotBlank @Size(max = 60) String> locations,
        @Size(max = 3)
        List<@Pattern(regexp = "ONSITE|HYBRID|REMOTE", message = "unknown work mode") String> workModes,
        @Size(max = 4)
        List<@Pattern(regexp = "FULL_TIME|PART_TIME|INTERNSHIP|CONTRACT", message = "unknown job type") String> jobTypes,
        @Min(value = 0, message = "salary can't be negative") @Max(value = 500, message = "salary looks too high")
        Integer minSalaryLpa,
        OffsetDateTime updatedAt) {
}
