package com.ticketing.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ticketing.domain.order.CompletedPurchase;

/**
 * Snapshot view of a single completed purchase. Captured at purchase time —
 * subsequent edits to the underlying Event (rename, price change, etc.) do
 * not affect the historical record.
 */
public record PurchaseRecordDTO(
        UUID purchaseId,
        UUID eventId,
        String eventName,
        String companyName,
        UUID memberId,
        String transactionId,
        BigDecimal amount,
        Instant purchasedAt
) {
    public static PurchaseRecordDTO from(CompletedPurchase p) {
        return new PurchaseRecordDTO(
                p.purchaseId(), p.eventId(), p.eventName(), p.companyName(), p.memberId(),
                p.transactionId(), p.amount(), p.purchasedAt());
    }
}
