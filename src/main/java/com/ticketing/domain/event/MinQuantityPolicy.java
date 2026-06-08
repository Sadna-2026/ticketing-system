package com.ticketing.domain.event;

/**
 * Rejects purchases when the total ticket count is below the required minimum.
 */
public class MinQuantityPolicy implements IPurchasePolicy {

    private final int minTickets;

    public MinQuantityPolicy(int minTickets) {
        if (minTickets <= 0) throw new IllegalArgumentException("minTickets must be positive");
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
