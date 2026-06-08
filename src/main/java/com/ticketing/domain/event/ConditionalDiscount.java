package com.ticketing.domain.event;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import com.ticketing.domain.order.ActiveOrder;

/**
 * A percentage discount that only applies when a condition (predicate) is met.
 * If the condition is not met, the original price is returned unchanged.
 * Example: "10% off when buying 2+ tickets", "15% off until May 15."
 */
public class ConditionalDiscount implements IDiscountPolicy {

    private final BigDecimal percentOff;
    private final IDiscountCondition condition;

    public ConditionalDiscount(BigDecimal percentOff, IDiscountCondition condition) {
        if (percentOff == null || percentOff.compareTo(BigDecimal.ZERO) <= 0
                || percentOff.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("percentOff must be between 0 (exclusive) and 100 (inclusive)");
        }
        if (condition == null) throw new IllegalArgumentException("condition is required");
        this.percentOff = percentOff;
        this.condition = condition;
    }

    public BigDecimal getPercentOff() { return percentOff; }
    public IDiscountCondition getCondition() { return condition; }

    @Override
    public BigDecimal priceAfterDiscount(ActiveOrder order, String couponCode, Instant systemClock) {
        if (!condition.isMet(order, systemClock)) {
            return order.getTotalPrice();
        }
        BigDecimal multiplier = BigDecimal.ONE.subtract(percentOff.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        return order.getTotalPrice().multiply(multiplier).setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
    }
}
