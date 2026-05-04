package com.ticketing.application;

import java.time.Duration;
import java.time.Instant;

/**
 * Test implementation of ISystemClock that allows manual time advancement
 * for deterministic testing (e.g., simulating lock expiration without waiting).
 */
public class TestClock implements ISystemClock {

    private Instant currentTime;

    public TestClock(Instant startTime) {
        this.currentTime = startTime;
    }

    public TestClock() {
        this(Instant.now());
    }

    @Override
    public Instant now() {
        return currentTime;
    }

    /**
     * Advances the clock by the given duration.
     */
    public void advance(Duration duration) {
        this.currentTime = this.currentTime.plus(duration);
    }

    /**
     * Sets the clock to a specific instant.
     */
    public void setTime(Instant time) {
        this.currentTime = time;
    }
}