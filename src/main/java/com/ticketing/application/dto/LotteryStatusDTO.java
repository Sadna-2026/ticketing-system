package com.ticketing.application.dto;

import java.time.Instant;
import java.util.UUID;

public record LotteryStatusDTO(
        boolean registered,
        UUID entryId,
        UUID eventId,
        UUID zoneId,
        int quantity,
        Instant registeredAt
) {
    public static LotteryStatusDTO registered(UUID entryId, UUID eventId, UUID zoneId, int quantity, Instant registeredAt) {
        return new LotteryStatusDTO(true, entryId, eventId, zoneId, quantity, registeredAt);
    }

    public static LotteryStatusDTO notRegistered(UUID eventId) {
        return new LotteryStatusDTO(false, null, eventId, null, 0, null);
    }
}
