package com.ticketing.domain.lottery;

import java.time.Instant;
import java.util.UUID;

/**
 * One member's entry in an event's ticket-allocation lottery. Aggregate root
 * for lottery operations. The draw outcome lives elsewhere (V2 — not yet wired).
 */
public record LotteryEntry(
        UUID id,
        UUID eventId,
        UUID memberId,
        UUID zoneId,
        int quantity,
        Instant registeredAt
) {
    public LotteryEntry {
        if (id == null) throw new IllegalArgumentException("id is required");
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (memberId == null) throw new IllegalArgumentException("memberId is required");
        if (zoneId == null) throw new IllegalArgumentException("zoneId is required");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (registeredAt == null) throw new IllegalArgumentException("registeredAt is required");
    }
}
