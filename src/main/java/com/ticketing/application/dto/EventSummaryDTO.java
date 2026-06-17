package com.ticketing.application.dto;

import java.util.UUID;

import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.SaleMethod;

public record EventSummaryDTO(
        UUID id,
        String name,
        String companyName,
        EventCategory category,
        EventSchedule schedule,
        EventStatus status,
        SaleMethod saleMethod
) {
    // Backwards-compat: existing 5-arg test calls (no saleMethod, no companyName)
    public EventSummaryDTO(UUID id, String name, EventCategory category, EventSchedule schedule, EventStatus status) {
        this(id, name, null, category, schedule, status, SaleMethod.REGULAR);
    }

    // Backwards-compat: existing 6-arg test calls (with saleMethod, no companyName)
    public EventSummaryDTO(UUID id, String name, EventCategory category, EventSchedule schedule, EventStatus status, SaleMethod saleMethod) {
        this(id, name, null, category, schedule, status, saleMethod);
    }

    public static EventSummaryDTO from(Event e) {
        return new EventSummaryDTO(e.getId(), e.getName(), e.getCompanyName(), e.getCategory(), e.getSchedule(), e.getStatus(), e.getSaleMethod());
    }
}
