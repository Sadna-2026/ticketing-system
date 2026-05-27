package com.ticketing.domain.event;

/**
 * Rejects purchases when the total ticket count exceeds the allowed maximum.
 */
public class MaxQuantityPolicy implements IPurchasePolicy {

    private final int maxTickets;

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
