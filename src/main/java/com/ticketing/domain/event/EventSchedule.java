package com.ticketing.domain.event;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Immutable value object: the event's start / end / doors-open instants.
 *
 * <p>Mapped as an {@code @Embeddable} embedded in {@link Event}. The {@code Instant}
 * columns carry an explicit {@code schedule_*} prefix so they do not collide with the
 * similarly-named instants on {@link LotteryWindow}. {@code final} was removed from the
 * fields so JPA can set them via field access; the public API is unchanged.
 */
@Embeddable
public class EventSchedule {

    @Column(name = "schedule_start_time")
    private Instant startTime;
    @Column(name = "schedule_end_time")
    private Instant endTime;
    @Column(name = "schedule_doors_open_time")
    private Instant doorsOpenTime;

    // Required by JPA; do not use directly.
    protected EventSchedule() {
    }

    public EventSchedule(Instant startTime, Instant endTime, Instant doorsOpenTime) {
        if (startTime == null) throw new IllegalArgumentException("Start time is required");
        if (endTime == null) throw new IllegalArgumentException("End time is required");
        if (endTime.isBefore(startTime)) throw new IllegalArgumentException("End time must be after start time");
        if (doorsOpenTime != null && doorsOpenTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Doors open time must be before or at start time");
        }
        this.startTime = startTime;
        this.endTime = endTime;
        this.doorsOpenTime = doorsOpenTime;
    }

    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public Instant getDoorsOpenTime() { return doorsOpenTime; }
    
}
