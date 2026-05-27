package com.ticketing.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketing.application.services.EventService;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.ZoneType;

/**
 * Public-facing event map + live inventory snapshot. Returned by
 * {@link EventService#getEventMap}. No staff data, no internals.
 */
public record EventMapDTO(
        UUID eventId,
        String eventName,
        String companyName,
        EventStatus status,
        Map<String, UUID> venueMap,
        List<ZoneInfo> zones
) {
    public EventMapDTO {
        venueMap = venueMap == null ? Map.of() : Map.copyOf(venueMap);
        zones    = zones == null    ? List.of() : List.copyOf(zones);
    }

    public record ZoneInfo(
            UUID id,
            String name,
            ZoneType type,
            BigDecimal pricePerTicket,
            // GA-only counters (null for assigned). lockedCount is intentionally
            // omitted — guests shouldn't see how many tickets are mid-purchase.
            Integer maxCapacity,
            Integer availableCount,
            Integer soldCount,
            // Assigned-only seat list (empty for GA)
            List<SeatInfo> seats
    ) {
        public ZoneInfo {
            seats = seats == null ? List.of() : List.copyOf(seats);
        }
    }

    /**
     * {@code available} collapses LOCKED + SOLD into a single "not available" signal —
     * the buyer doesn't need to distinguish "someone else is buying it" from "already gone".
     */
    public record SeatInfo(UUID id, String row, String seatNumber, boolean available) {}
}
