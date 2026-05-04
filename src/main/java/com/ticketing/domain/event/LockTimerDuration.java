package com.ticketing.domain.event;

import java.time.Duration;

public final class LockTimerDuration {

    private final Duration duration;

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
