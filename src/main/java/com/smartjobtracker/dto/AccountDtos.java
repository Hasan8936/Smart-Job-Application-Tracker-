package com.smartjobtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class AccountDtos {
    private AccountDtos() {}

    /** Optional http(s) scheme, a domain, optional path; the service adds https:// when missing. */
    private static final String WEB_URL = "^$|^(?i)(https?://)?[a-z0-9-]+(\\.[a-z0-9-]+)+(:[0-9]{1,5})?(/\\S*)?$";
    private static final String PHONE = "^$|^[+0-9 ()\\-.]{5,30}$";

    /** Editable profile details. {@code email} is returned for display only and ignored on update. */
    public record Details(
            @Size(max = 100) String name,
            String email,
            @Size(max = 200) String headline,
            @Size(max = 200) String location,
            @Pattern(regexp = PHONE, message = "must be a phone number (digits, spaces, + - ( ) .)") String phone,
            @Size(max = 500) @Pattern(regexp = WEB_URL, message = "must be a web address like linkedin.com/in/you") String linkedinUrl,
            @Size(max = 500) @Pattern(regexp = WEB_URL, message = "must be a web address like linkedin.com/in/you") String githubUrl,
            @Size(max = 500) @Pattern(regexp = WEB_URL, message = "must be a web address like linkedin.com/in/you") String websiteUrl) {}

    /** {@code currentPassword} may be empty only when the account never set a password (Google sign-up). */
    public record ChangePassword(@Size(max = 128) String currentPassword,
                                 @NotBlank @Size(min = 8, max = 128) String newPassword) {}

    /** Accounts with a password confirm with it; Google-only accounts confirm by typing their email. */
    public record DeleteAccount(@Size(max = 128) String password, @Size(max = 320) String confirmEmail) {}

    public record PhotoInfo(boolean hasPhoto, OffsetDateTime updatedAt) {}
}
