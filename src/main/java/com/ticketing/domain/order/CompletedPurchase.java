package com.ticketing.domain.order;

import java.math.BigDecimal;
import java.util.UUID;

public record CompletedPurchase(
        UUID purchaseId,
        UUID eventId,
        String companyName,
        UUID memberId,
        String transactionId,
        BigDecimal amount
) {
    public CompletedPurchase {
        if (purchaseId == null) throw new IllegalArgumentException("purchaseId is required");
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("companyName is required");
        }
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId is required");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
    }
}
