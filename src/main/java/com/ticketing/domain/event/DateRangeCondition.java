package com.ticketing.domain.event;

import java.time.Instant;

import com.ticketing.domain.order.ActiveOrder;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Condition: the current time falls within [from, to].
 * Example: "15% off for purchases made before May 15."
 */
@Entity
@DiscriminatorValue("DATE_RANGE")
public class DateRangeCondition extends AbstractDiscountCondition {

    @Column(name = "from_instant")
    private Instant from; // nullable = no lower bound
    @Column(name = "to_instant")
    private Instant to;   // nullable = no upper bound

    // Required by JPA; do not use directly.
    protected DateRangeCondition() {
    }

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
