package com.ticketing.domain.lottery;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One member's pre-registration in an event's purchase-right lottery (requirement
 * §II.3.6). A member simply enters the lottery in advance; the zone, seats and
 * quantity are chosen later, by the winner, during the ticket-reservation stage —
 * so an entry only records who registered for which event and when.
 *
 * <p>JPA mapping (V3-8, #266): an immutable {@code @Entity} (field access). Was a
 * {@code record} in V2; converted to a class because JPA cannot instantiate a record
 * (no no-arg constructor / final components). The record-style accessors
 * ({@link #id()}, {@link #eventId()}, {@link #memberId()}, {@link #registeredAt()},
 * {@link #version()}), the two constructors, {@link #incrementedVersionCopy()}, and
 * {@code equals}/{@code hashCode} are preserved so the public API stays record-like.
 *
 * <p>{@code version} is mapped with {@code @Version} for JPA optimistic locking; it
 * keeps the same CAS semantics the in-memory repository relied on (a fresh entry is
 * stored at version 1 via {@link #incrementedVersionCopy()}; a stale save is rejected).
 */
@Entity
@Table(name = "lottery_entries")
public class LotteryEntry {

    @Id
    @Column(name = "id")
    private UUID id;
    @Column(name = "event_id")
    private UUID eventId;
    @Column(name = "member_id")
    private UUID memberId;
    @Column(name = "registered_at")
    private Instant registeredAt;
    @Version
    @Column(name = "version")
    private int version;

    // Required by JPA; do not use directly.
    protected LotteryEntry() {
    }

    public LotteryEntry(UUID id, UUID eventId, UUID memberId, Instant registeredAt) {
        this(id, eventId, memberId, registeredAt, 0);
    }

    public LotteryEntry(UUID id, UUID eventId, UUID memberId, Instant registeredAt, int version) {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (memberId == null) throw new IllegalArgumentException("memberId is required");
        if (registeredAt == null) throw new IllegalArgumentException("registeredAt is required");
        if (version < 0) throw new IllegalArgumentException("version cannot be negative");
        this.id = id;
        this.eventId = eventId;
        this.memberId = memberId;
        this.registeredAt = registeredAt;
        this.version = version;
    }

    // Record-style accessors, preserved so all existing call sites compile unchanged.
    public UUID id() { return id; }
    public UUID eventId() { return eventId; }
    public UUID memberId() { return memberId; }
    public Instant registeredAt() { return registeredAt; }
    public int version() { return version; }

    public LotteryEntry incrementedVersionCopy() {
        return new LotteryEntry(id, eventId, memberId, registeredAt, version + 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LotteryEntry that = (LotteryEntry) o;
        return version == that.version
                && Objects.equals(id, that.id)
                && Objects.equals(eventId, that.eventId)
                && Objects.equals(memberId, that.memberId)
                && Objects.equals(registeredAt, that.registeredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, eventId, memberId, registeredAt, version);
    }

    @Override
    public String toString() {
        return "LotteryEntry[id=" + id
                + ", eventId=" + eventId
                + ", memberId=" + memberId
                + ", registeredAt=" + registeredAt
                + ", version=" + version + "]";
    }
}
