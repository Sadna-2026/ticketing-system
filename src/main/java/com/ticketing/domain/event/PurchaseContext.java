package com.ticketing.domain.event;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.ticketing.domain.order.ActiveOrder;

/**
 * Carries all buyer/order information a purchase policy might need.
 * Nullable fields (memberId, buyerDateOfBirth) are null for guest purchases.
 */
public record PurchaseContext(
        ActiveOrder order,
        UUID memberId,
        LocalDate buyerDateOfBirth,
        Event event,
        Set<UUID> incomingSeatIds
) {
    public PurchaseContext(ActiveOrder order, UUID memberId, LocalDate buyerDateOfBirth) {
        this(order, memberId, buyerDateOfBirth, null, Set.of());
    }
}
