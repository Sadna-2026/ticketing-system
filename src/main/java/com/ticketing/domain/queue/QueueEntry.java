package com.ticketing.domain.queue;


import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.ticketing.application.dto.QueueEntryDto;

/**
 * Entity within the VirtualQueue aggregate.
 *
 * Represents a user waiting in the virtual queue for a specific event.
 */
public class QueueEntry {

    private final UUID id;
    private final UUID sessionId;
    private final Instant joinedAt;
    private QueueEntryStatus status;

    public QueueEntry(UUID id, UUID sessionId, Instant joinedAt) {
        if (id == null) throw new IllegalArgumentException("QueueEntry ID is required");
        if (sessionId == null) throw new IllegalArgumentException("Session ID is required");
        if (joinedAt == null) throw new IllegalArgumentException("JoinedAt is required");
        this.id = id;
        this.sessionId = sessionId;
        this.joinedAt = joinedAt;
        this.status = QueueEntryStatus.WAITING;
    }

    private QueueEntry(UUID id, UUID sessionId, Instant joinedAt, QueueEntryStatus status) {
        this(id, sessionId, joinedAt);
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public Instant getJoinedAt() { return joinedAt; }
    public QueueEntryStatus getStatus() { return status; }

    public boolean isWaiting() { return status == QueueEntryStatus.WAITING; }

    public void admit() {
        if (status != QueueEntryStatus.WAITING) throw new IllegalStateException("Can only admit WAITING entries");
        this.status = QueueEntryStatus.ADMITTED;
    }

    public void leave() {
        this.status = QueueEntryStatus.LEFT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueueEntry that = (QueueEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public QueueEntryDto toQueueDto() {
        return new QueueEntryDto(id, sessionId, joinedAt, status.name());
    }

    public QueueEntry detachedCopy() {
        return new QueueEntry(id, sessionId, joinedAt, status);
    }
}
