package com.ticketing.application;

import java.util.List;

/**
 * Public-facing view of a company, safe to return to guests/members.
 * Deliberately omits founderId, status, and any staff-appointment data.
 */
public record CompanyPublicDTO(
        String name,
        String description,
        List<EventSummaryDTO> events
) {
    public CompanyPublicDTO {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
