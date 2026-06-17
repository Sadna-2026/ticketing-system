package com.ticketing.domain.event;

public enum EventStatus {
    DRAFT,
    PUBLISHED,
    SOLD_OUT,
    /** Cancellation requested: new purchases are blocked while orders are released and refunds run. */
    CANCELLATION_IN_PROGRESS,
    CANCELLED,
    /** Cancelled, but one or more refunds are still pending/failed and need a retry. */
    CANCELLED_WITH_PENDING_REFUNDS
}
