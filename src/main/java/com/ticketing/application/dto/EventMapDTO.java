package com.ticketing.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketing.application.services.EventService;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.LayoutCellType;
import com.ticketing.domain.event.SaleMethod;
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
        List<ZoneInfo> zones,
        // Optional visual grid layout (null when the event has no designed layout).
        LayoutInfo layout,
        String description,
        List<EventPolicyBadgeDTO> purchaseRestrictions,
        List<EventPolicyBadgeDTO> visibleDiscounts,
        SaleMethod saleMethod,
        Instant lotteryRegistrationOpen,
        Instant lotteryRegistrationClose,
        boolean lotteryWindowOpen
) {
    public EventMapDTO {
        venueMap = venueMap == null ? Map.of() : Map.copyOf(venueMap);
        zones    = zones == null    ? List.of() : List.copyOf(zones);
        description = description == null ? "" : description;
        purchaseRestrictions = purchaseRestrictions == null ? List.of() : List.copyOf(purchaseRestrictions);
        visibleDiscounts = visibleDiscounts == null ? List.of() : List.copyOf(visibleDiscounts);
        saleMethod = saleMethod == null ? SaleMethod.REGULAR : saleMethod;
    }

    /** Backwards-compatible constructor for callers that don't carry a layout. */
    public EventMapDTO(UUID eventId, String eventName, String companyName, EventStatus status,
                       Map<String, UUID> venueMap, List<ZoneInfo> zones) {
        this(eventId, eventName, companyName, status, venueMap, zones, null, "", List.of(), List.of(),
                null, null, null, false);
    }

    /** Backwards-compatible constructor for callers that don't carry policy metadata. */
    public EventMapDTO(UUID eventId, String eventName, String companyName, EventStatus status,
                       Map<String, UUID> venueMap, List<ZoneInfo> zones, LayoutInfo layout) {
        this(eventId, eventName, companyName, status, venueMap, zones, layout, "", List.of(), List.of(),
                null, null, null, false);
    }

    /** Backwards-compatible constructor for callers that don't carry lottery metadata. */
    public EventMapDTO(UUID eventId, String eventName, String companyName, EventStatus status,
                       Map<String, UUID> venueMap, List<ZoneInfo> zones, LayoutInfo layout, String description,
                       List<EventPolicyBadgeDTO> purchaseRestrictions, List<EventPolicyBadgeDTO> visibleDiscounts) {
        this(eventId, eventName, companyName, status, venueMap, zones, layout, description,
                purchaseRestrictions, visibleDiscounts, null, null, null, false);
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

    /** A visual grid layout for spatial rendering of the hall. */
    public record LayoutInfo(int rows, int cols, List<CellInfo> cells) {
        public LayoutInfo {
            cells = cells == null ? List.of() : List.copyOf(cells);
        }
    }

    /** One placed cell in the grid. {@code zoneId}/{@code seatId} are null for non-sellable cells. */
    public record CellInfo(int row, int col, LayoutCellType type, String label, UUID zoneId, UUID seatId) {}
}
