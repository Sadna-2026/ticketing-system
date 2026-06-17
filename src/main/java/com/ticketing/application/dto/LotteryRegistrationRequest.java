package com.ticketing.application.dto;

import java.util.UUID;

/**
 * A member's request to enter an event's purchase-right lottery (requirement §II.3.6).
 * Registration is a simple advance sign-up — the winner picks zone, seats and quantity
 * later, during the ticket-reservation stage — so only the event is required.
 */
public record LotteryRegistrationRequest(UUID eventId) {

    public LotteryRegistrationRequest {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
    }
}
