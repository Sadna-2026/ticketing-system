package com.ticketing.domain.event;

import java.time.LocalDate;
import java.util.UUID;

import com.ticketing.domain.order.ActiveOrder;

/**
 * Carries all buyer/order information a purchase policy might need.
 * Nullable fields (memberId, buyerDateOfBirth) are null for guest purchases.
 */
public record PurchaseContext(ActiveOrder order, UUID memberId, LocalDate buyerDateOfBirth) {
}
