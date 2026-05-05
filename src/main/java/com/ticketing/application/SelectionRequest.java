package com.ticketing.application;

import java.util.List;
import java.util.UUID;

/**
 * Pre-reservation selection request. Supports the two ticket modes side-by-side:
 *  - {@code seats} for assigned-seating zones (specific seat IDs)
 *  - {@code gaQuantities} for general-admission zones (count per zone)
 *
 * Either list may be empty; at least one item must be present overall —
 * an empty selection is rejected by the service.
 */
public record SelectionRequest(
        UUID eventId,
        List<SeatPick> seats,
        List<GAPick> gaQuantities
) {
    public SelectionRequest {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        seats        = seats        == null ? List.of() : List.copyOf(seats);
        gaQuantities = gaQuantities == null ? List.of() : List.copyOf(gaQuantities);

        // Reject duplicate seat ids — picking the same seat twice can never succeed
        // and would only surface as a confusing error at the locking stage.
        java.util.Set<UUID> seenSeatIds = new java.util.HashSet<>();
        for (SeatPick p : seats) {
            if (!seenSeatIds.add(p.seatId())) {
                throw new IllegalArgumentException("Duplicate seat in selection: " + p.seatId());
            }
        }
    }

    public boolean isEmpty() {
        return seats.isEmpty() && gaQuantities.isEmpty();
    }

    public record SeatPick(UUID zoneId, UUID seatId) {
        public SeatPick {
            if (zoneId == null) throw new IllegalArgumentException("zoneId is required");
            if (seatId == null) throw new IllegalArgumentException("seatId is required");
        }
    }

    public record GAPick(UUID zoneId, int quantity) {
        public GAPick {
            if (zoneId == null) throw new IllegalArgumentException("zoneId is required");
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
