package com.ticketing.application;

import java.util.UUID;

import com.ticketing.application.services.EventService;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.LotteryWindow;

/**
 * Edit request for {@link EventService#editEvent}. Any null field means
 * "leave it as-is"; non-null means apply the new value. {@code eventId}
 * is required.
 */
public record EditEventRequest(
        UUID eventId,
        String name,
        String description,
        String artist,
        EventSchedule schedule,
        LotteryWindow lotteryWindow
) {
    public EditEventRequest {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (name != null && name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank if provided");
        }
    }

    /** Backwards-compat constructor without lotteryWindow. */
    public EditEventRequest(UUID eventId, String name, String description, String artist, EventSchedule schedule) {
        this(eventId, name, description, artist, schedule, null);
    }

    public boolean hasAnyChange() {
        return name != null || description != null || artist != null || schedule != null || lotteryWindow != null;
    }
}
