package com.ticketing.domain.queue;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One member or guest waiting in the virtual queue for a high-demand event.
 * Aggregate root for queue-related operations.
 *
 * Two distinct UUIDs by design:
 *   {@code id}        — this QueueSession's own aggregate identity (repo key).
 *   {@code sessionId} — the user-side HTTP session sitting in the queue, used
 *                       to answer "am I (this browser) currently in a queue?".
 *                       Not a foreign key to a database row — it's the same
 *                       sessionId carried in the JWT.
 */
public class QueueSession {

    private final UUID id;
    private final UUID sessionId;
    private final UUID eventId;
    private int position;
    private final Instant joinedAt;

    public QueueSession(UUID id, UUID sessionId, UUID eventId, int position, Instant joinedAt) {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (sessionId == null) throw new IllegalArgumentException("sessionId is required");
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (position < 0) throw new IllegalArgumentException("position must be >= 0");
        if (joinedAt == null) throw new IllegalArgumentException("joinedAt is required");
        this.id = id;
        this.sessionId = sessionId;
        this.eventId = eventId;
        this.position = position;
        this.joinedAt = joinedAt;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public UUID getEventId() { return eventId; }
    public int getPosition() { return position; }
    public Instant getJoinedAt() { return joinedAt; }

    public void advance() {
        if (position == 0) throw new IllegalStateException("already at front of queue");
        position--;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueueSession q)) return false;
        return Objects.equals(id, q.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
