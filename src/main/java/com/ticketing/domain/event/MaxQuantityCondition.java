package com.ticketing.domain.event;

import java.time.Instant;

import com.ticketing.domain.order.ActiveOrder;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Condition: order has at most {@code maxTickets} tickets.
 */
@Entity
@DiscriminatorValue("MAX_QUANTITY")
public class MaxQuantityCondition extends AbstractDiscountCondition {

    @Column(name = "max_tickets")
    private int maxTickets;

    // Required by JPA; do not use directly.
    protected MaxQuantityCondition() {
    }

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
