package com.ticketing.domain.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pending_notifications")
public class PendingNotification {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // false until delivered to the member (e.g. flushed as a toast on login). Retained after
    // delivery so the Notifications tab can show it as history rather than dropping it (#490).
    @Column(name = "seen", nullable = false)
    private boolean seen;

    protected PendingNotification() {} // JPA

    public PendingNotification(String userId, String message) {
        this(userId, message, false);
    }

    public PendingNotification(String userId, String message, boolean seen) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.message = message;
        this.createdAt = Instant.now();
        this.seen = seen;
    }

    public boolean isSeen() {
        return seen;
    }

    public void markSeen() {
        this.seen = true;
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
