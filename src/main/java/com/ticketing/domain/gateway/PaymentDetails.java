package com.ticketing.domain.gateway;

import java.util.UUID;

/**
 * Payment context for a charge. The buyer-entered card fields ({@code currency}..{@code cardId})
 * are populated from the checkout dialog and sent to the WSEP {@code pay} action; when null the
 * gateway falls back to its configured sandbox card, so legacy/seed flows keep working.
 */
public record PaymentDetails(
        UUID orderId,
        UUID eventId,
        UUID memberId,
        String email,
        String currency,
        String cardNumber,
        String month,
        String year,
        String holder,
        String cvv,
        String cardId) {

    /** Backward-compatible constructor without card data (gateway uses its configured card). */
    public PaymentDetails(UUID orderId, UUID eventId, UUID memberId, String email) {
        this(orderId, eventId, memberId, email, null, null, null, null, null, null, null);
    }
}
