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
        EventCategory category,
        EventSchedule schedule,
        EventStatus status,
        SaleMethod saleMethod
) {
    public static EventSummaryDTO from(Event e) {
        return new EventSummaryDTO(e.getId(), e.getName(), e.getCategory(), e.getSchedule(), e.getStatus(),
                e.getSaleMethod() != null ? e.getSaleMethod() : SaleMethod.REGULAR);
    }
}
