package com.ticketing.domain.order;

/**
 * Refund lifecycle of a {@link CompletedPurchase} for the cancel-event use case.
 * {@code NONE} is the default for a normal paid purchase; the others are set during cancellation.
 */
public enum RefundStatus {
    NONE,
    PENDING,
    REFUNDED,
    FAILED
}
