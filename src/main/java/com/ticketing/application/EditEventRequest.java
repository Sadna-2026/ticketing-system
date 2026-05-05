package com.ticketing.application;

import java.util.UUID;

import com.ticketing.domain.event.EventSchedule;

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
        EventSchedule schedule
) {
    public EditEventRequest {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (name != null && name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank if provided");
        }
    }

    public boolean hasAnyChange() {
        return name != null || description != null || artist != null || schedule != null;
    }
}
