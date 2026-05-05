package com.ticketing.domain.event;

import java.time.Instant;

public final class EventSchedule {

    private final Instant startTime;
    private final Instant endTime;
    private final Instant doorsOpenTime;

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
