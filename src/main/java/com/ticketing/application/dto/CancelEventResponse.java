package com.ticketing.application.dto;

import java.util.UUID;

/**
 * Result of the cancel-event use case (V3-CANCEL-EVENT): how many active orders were cancelled,
 * how many completed purchases were found, and the success/pending/failed refund tallies, plus the
 * resulting cancellation status ({@code CANCELLED} or {@code CANCELLED_WITH_PENDING_REFUNDS}).
 */
public record CancelEventResponse(
        boolean success,
        String message,
        UUID eventId,
        int activeOrdersCancelled,
        int purchasesFound,
        int refundsSucceeded,
        int refundsPending,
        int refundsFailed,
        String cancellationStatus
) {
}
