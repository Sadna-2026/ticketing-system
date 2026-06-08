package com.ticketing.domain.event;

import java.time.Duration;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Immutable value object: how long a ticket lock is held before it expires.
 *
 * <p>Mapped as an {@code @Embeddable} embedded in {@link Event}. {@code final} was
 * removed from the field so JPA can set it via field access; the public API is
 * unchanged.
 */
@Embeddable
public class LockTimerDuration {

    @Column(name = "lock_timer_duration")
    private Duration duration;

    // Required by JPA; do not use directly.
    protected LockTimerDuration() {
    }

    public LockTimerDuration(Duration duration) {
        if (duration == null) throw new IllegalArgumentException("Duration is required");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        this.duration = duration;
    }

    public Duration getDuration() { return duration; }

    public long toMillis() { return duration.toMillis(); }
    public long toSeconds() { return duration.toSeconds(); }
    public long toMinutes() { return duration.toMinutes(); }
}
