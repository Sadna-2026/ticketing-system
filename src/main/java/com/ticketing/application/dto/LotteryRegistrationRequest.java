package com.ticketing.application.dto;

import java.util.UUID;

public record LotteryRegistrationRequest(UUID eventId, UUID zoneId, int quantity) {

    public LotteryRegistrationRequest {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (zoneId == null) throw new IllegalArgumentException("zoneId is required");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
    }
}
