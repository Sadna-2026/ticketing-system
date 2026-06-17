package com.ticketing.domain.event;

import java.time.Instant;

import com.ticketing.domain.order.ActiveOrder;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Condition: order has at least {@code minTickets} tickets.
 * Example: "10% off when buying 2 or more tickets."
 */
@Entity
@DiscriminatorValue("MIN_QUANTITY")
public class MinQuantityCondition extends AbstractDiscountCondition {

    @Column(name = "min_tickets")
    private int minTickets;

    // Required by JPA; do not use directly.
    protected MinQuantityCondition() {
    }

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
