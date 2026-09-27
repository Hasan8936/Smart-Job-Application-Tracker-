package com.smartjobtracker.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** One message in a support ticket conversation; the first one is the user's description. */
@Entity
@Table(name = "support_ticket_messages")
public class SupportTicketMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "ticket_id", nullable = false) private Long ticketId;
    @Column(name = "author_id") private Long authorId;
    @Column(name = "from_admin", nullable = false) private boolean fromAdmin;
    @Column(nullable = false, columnDefinition = "text") private String body;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getTicketId() { return ticketId; } public void setTicketId(Long v) { ticketId = v; }
    public Long getAuthorId() { return authorId; } public void setAuthorId(Long v) { authorId = v; }
    public boolean isFromAdmin() { return fromAdmin; } public void setFromAdmin(boolean v) { fromAdmin = v; }
    public String getBody() { return body; } public void setBody(String v) { body = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
}
