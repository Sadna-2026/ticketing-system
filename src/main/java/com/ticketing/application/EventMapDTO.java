package com.ticketing.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.SeatStatus;
import com.ticketing.domain.event.ZoneType;

/**
 * Public-facing event map + live inventory snapshot. Returned by
 * {@link EventQueryService#getEventMap}. No staff data, no internals.
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
            // GA-only counters (null for assigned)
            Integer maxCapacity,
            Integer availableCount,
            Integer lockedCount,
            Integer soldCount,
            // Assigned-only seat list (empty for GA)
            List<SeatInfo> seats
    ) {
        public ZoneInfo {
            seats = seats == null ? List.of() : List.copyOf(seats);
        }
    }

    public record SeatInfo(UUID id, String row, String seatNumber, SeatStatus status) {}
}
