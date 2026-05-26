package com.ticketing.domain.event;

import java.time.Instant;

import com.ticketing.domain.order.ActiveOrder;

/**
 * Condition: order has at most {@code maxTickets} tickets.
 */
public class MaxQuantityCondition implements IDiscountCondition {

    private final int maxTickets;

    public MaxQuantityCondition(int maxTickets) {
        if (maxTickets <= 0) throw new IllegalArgumentException("maxTickets must be positive");
        this.maxTickets = maxTickets;
    }

    public int getMaxTickets() { return maxTickets; }

    @Override
    public boolean isMet(ActiveOrder order, Instant now) {
        return order.getTotalTicketCount() <= maxTickets;
    }
}
