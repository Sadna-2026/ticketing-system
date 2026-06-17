package com.ticketing.application.scheduling;

import java.util.UUID;

/**
 * Published by {@code EventService} when a lottery event is published or its registration
 * window is edited, so {@code LotteryDrawScheduler} can (re)arm the one-shot automatic draw.
 * Decouples the service from the scheduler — the service never holds a scheduler reference.
 */
public record LotteryScheduleEvent(UUID eventId) {

    public LotteryScheduleEvent {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
    }
}
