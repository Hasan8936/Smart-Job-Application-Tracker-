package com.smartjobtracker.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** A user's profile photo, already re-encoded server-side (metadata stripped). Stored in the DB: Render's disk is ephemeral. */
@Entity
@Table(name = "user_photos")
public class UserPhoto {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "content_type", nullable = false, length = 20)
    private String contentType;

    // VARBINARY → bytea on PostgreSQL (matches V29) and binary varying on H2. A processed photo (≤512×512) is well under 1MB.
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARBINARY)
    @Column(name = "data", nullable = false, length = 1_000_000)
    private byte[] data;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
