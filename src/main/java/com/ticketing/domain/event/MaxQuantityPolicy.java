package com.ticketing.domain.event;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Rejects purchases when the total ticket count exceeds the allowed maximum.
 */
@Entity
@DiscriminatorValue("MAX_QUANTITY")
public class MaxQuantityPolicy extends AbstractPurchasePolicy {

    @Column(name = "max_tickets")
    private int maxTickets;

    // Required by JPA; do not use directly.
    protected MaxQuantityPolicy() {
    }

    public MaxQuantityPolicy(int maxTickets) {
        if (maxTickets <= 0) throw new IllegalArgumentException("maxTickets must be positive");
        this.maxTickets = maxTickets;
    }

    public int getMaxTickets() { return maxTickets; }

    @Override
    public PolicyResult isAllowed(PurchaseContext context) {
        int count = context.order().getTotalTicketCount();
        if (count > maxTickets) {
            return PolicyResult.failure("MAX_QUANTITY_EXCEEDED",
                    "You can purchase at most " + maxTickets + " tickets");
        }
        return PolicyResult.success();
    }
}
