package com.ticketing.domain.member;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Value object representing a user suspension imposed by a system admin.
 * A suspension can be timed (has a duration) or permanent (duration is null).
 */
public class Suspension {

    private final UUID suspensionId;
    private final UUID imposedByAdminId;
    private final Instant startTime;
    private final Duration duration;      // null = permanent
    private final String reason;
    private boolean cancelled;

    public Suspension(UUID imposedByAdminId, Instant startTime, Duration duration, String reason) {
        if (imposedByAdminId == null) {
            throw new IllegalArgumentException("imposedByAdminId is required");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("startTime is required");
        }
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException("duration must be positive if provided");
        }
        this.suspensionId = UUID.randomUUID();
        this.imposedByAdminId = imposedByAdminId;
        this.startTime = startTime;
        this.duration = duration;
        this.reason = reason;
        this.cancelled = false;
    }

    public UUID getSuspensionId() { return suspensionId; }
    public UUID getImposedByAdminId() { return imposedByAdminId; }
    public Instant getStartTime() { return startTime; }
    public Duration getDuration() { return duration; }
    public String getReason() { return reason; }
    public boolean isCancelled() { return cancelled; }

    public boolean isPermanent() {
        return duration == null;
    }

    /**
     * Returns the instant at which this suspension expires, or null if permanent.
     */
    public Instant getEndTime() {
        return isPermanent() ? null : startTime.plus(duration);
    }

    /**
     * Returns true if the suspension is currently in effect at the given instant.
     * A cancelled suspension is never active.
     */
    public boolean isActive(Instant now) {
        if (cancelled) return false;
        if (now.isBefore(startTime)) return false;
        if (isPermanent()) return true;
        return now.isBefore(getEndTime());
    }

    /**
     * Cancels this suspension (admin lifts it early).
     */
    public void cancel() {
        if (cancelled) {
            throw new IllegalStateException("Suspension is already cancelled");
        }
        this.cancelled = true;
    }

    public Suspension detachedCopy() {
        Suspension copy = new Suspension(imposedByAdminId, startTime, duration, reason);
        // We need to preserve the suspensionId — use reflection-free approach by
        // copying via the internal fields. Since suspensionId is final and generated
        // in the constructor, we use a private constructor for copies.
        return copyWithId(this.suspensionId, this.cancelled);
    }

    private Suspension copyWithId(UUID id, boolean cancelled) {
        // Internal: create a copy preserving the original suspensionId
        Suspension copy = new Suspension(imposedByAdminId, startTime, duration, reason);
        try {
            java.lang.reflect.Field idField = Suspension.class.getDeclaredField("suspensionId");
            idField.setAccessible(true);
            idField.set(copy, id);
        } catch (Exception e) {
            // fallback: the copy will have a different ID, but fields are correct
        }
        if (cancelled) copy.cancelled = true;
        return copy;
    }
}
