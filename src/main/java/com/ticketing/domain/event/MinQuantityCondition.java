package com.ticketing.domain.event;

import java.time.Instant;

import com.ticketing.domain.order.ActiveOrder;

/**
 * Condition: order has at least {@code minTickets} tickets.
 * Example: "10% off when buying 2 or more tickets."
 */
public class MinQuantityCondition implements IDiscountCondition {

    private final int minTickets;

    public MinQuantityCondition(int minTickets) {
        if (minTickets <= 0) throw new IllegalArgumentException("minTickets must be positive");
        this.minTickets = minTickets;
    }

    public int getMinTickets() { return minTickets; }

    @Override
    public boolean isMet(ActiveOrder order, Instant now) {
        return order.getTotalTicketCount() >= minTickets;
    }
}
