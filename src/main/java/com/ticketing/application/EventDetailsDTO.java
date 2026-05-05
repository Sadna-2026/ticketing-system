package com.ticketing.application;

import java.util.UUID;

import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;

public record EventDetailsDTO(
        UUID id,
        String companyName,
        String name,
        String description,
        String artist,
        EventCategory category,
        EventSchedule schedule,
        EventStatus status
) {
    public static EventDetailsDTO from(Event e) {
        return new EventDetailsDTO(
                e.getId(),
                e.getCompanyName(),
                e.getName(),
                e.getDescription(),
                e.getArtist(),
                e.getCategory(),
                e.getSchedule(),
                e.getStatus());
    }
}
