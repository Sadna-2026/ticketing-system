package com.ticketing.domain.event;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Rejects purchases when the total ticket count is below the required minimum.
 */
@Entity
@DiscriminatorValue("MIN_QUANTITY")
public class MinQuantityPolicy extends AbstractPurchasePolicy {

    @Column(name = "min_tickets")
    private int minTickets;

    // Required by JPA; do not use directly.
    protected MinQuantityPolicy() {
    }

    public MinQuantityPolicy(int minTickets) {
        if (minTickets < 2) {
            throw new IllegalArgumentException("minTickets must be at least 2");
        }
        this.minTickets = minTickets;
    }

    public int getMinTickets() { return minTickets; }

    @Override
    public PolicyResult isAllowed(PurchaseContext context) {
        int count = context.order().getTotalTicketCount();
        if (count < minTickets) {
            return PolicyResult.failure("MIN_QUANTITY_NOT_MET",
                    "You must purchase at least " + minTickets + " tickets");
        }
        return PolicyResult.success();
    }
}
