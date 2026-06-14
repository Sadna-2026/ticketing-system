package com.ticketing.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ticketing.domain.order.CompletedPurchase;

/**
 * Snapshot view of a single completed purchase. Captured at purchase time —
 * subsequent edits to the underlying Event (rename, price change, etc.) or to
 * the buyer (rename, removal) do not affect the historical record. {@code
 * buyerUsername} is the buyer's username as it was at purchase time, so the
 * admin global-history grid can show a human-readable buyer without resolving
 * the id against the current member list.
 */
public record PurchaseRecordDTO(
        UUID purchaseId,
        UUID eventId,
        String eventName,
        String companyName,
        UUID memberId,
        String buyerUsername,
        String transactionId,
        BigDecimal amount,
        Instant purchasedAt
) {
    /** Backward-compatible constructor for records built without a buyer-username snapshot. */
    public PurchaseRecordDTO(
            UUID purchaseId,
            UUID eventId,
            String eventName,
            String companyName,
            UUID memberId,
            String transactionId,
            BigDecimal amount,
            Instant purchasedAt
    ) {
        this(purchaseId, eventId, eventName, companyName, memberId, null,
                transactionId, amount, purchasedAt);
    }

    public static PurchaseRecordDTO from(CompletedPurchase p) {
        return new PurchaseRecordDTO(
                p.purchaseId(), p.eventId(), p.eventName(), p.companyName(), p.memberId(),
                p.buyerUsername(), p.transactionId(), p.amount(), p.purchasedAt());
    }
}
