package com.ticketing.domain.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A frozen record of a completed purchase. Snapshot fields (eventName, amount,
 * purchasedAt) are captured at checkout time and never reflect later changes
 * to the underlying Event.
 *
 * NOTE for the checkout-flow caller (UC-II.12): pass {@code ISystemClock.now()}
 * for {@code purchasedAt} rather than {@link java.time.Instant#now()} directly,
 * so tests can use TestClock and pin timestamps deterministically.
 */
public record CompletedPurchase(
        UUID purchaseId,
        UUID eventId,
        String eventName,
        String companyName,
        UUID memberId,
        String transactionId,
        BigDecimal amount,
        Instant purchasedAt
) {
    public CompletedPurchase {
        if (purchaseId == null) throw new IllegalArgumentException("purchaseId is required");
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("eventName is required");
        }
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("companyName is required");
        }
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId is required");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (purchasedAt == null) {
            throw new IllegalArgumentException("purchasedAt is required");
        }
    }
}
