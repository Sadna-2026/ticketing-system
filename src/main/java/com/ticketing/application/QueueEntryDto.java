package com.ticketing.application;


import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for a queue entry in a virtual queue.
 */
public final class QueueEntryDto {

    private final UUID id;
    private final UUID sessionId;
    private final Instant joinedAt;
    private final String status;

    public QueueEntryDto(UUID id, UUID sessionId, Instant joinedAt, String status) {
        this.id = id;
        this.sessionId = sessionId;
        this.joinedAt = joinedAt;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public Instant getJoinedAt() { return joinedAt; }
    public String getStatus() { return status; }
}
