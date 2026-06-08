package com.ticketing.domain.event;

import java.time.Instant;

import com.ticketing.domain.order.ActiveOrder;

/**
 * Condition: the current time falls within [from, to].
 * Example: "15% off for purchases made before May 15."
 */
public class DateRangeCondition implements IDiscountCondition {

    private final Instant from; // nullable = no lower bound
    private final Instant to;   // nullable = no upper bound

    public DateRangeCondition(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        this.from = from;
        this.to = to;
    }

    public Instant getFrom() { return from; }
    public Instant getTo() { return to; }

    @Override
    public boolean isMet(ActiveOrder order, Instant now) {
        if (from != null && now.isBefore(from)) return false;
        if (to != null && now.isAfter(to)) return false;
        return true;
    }
}
