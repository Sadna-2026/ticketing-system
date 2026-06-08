package com.ticketing.domain.event;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import com.ticketing.domain.order.ActiveOrder;

/**
 * A flat percentage discount applied unconditionally.
 * Example: "10% off the total price."
 */
public class SimpleDiscount implements IDiscountPolicy {

    private final BigDecimal percentOff; // e.g. 10 means 10%

    public SimpleDiscount(BigDecimal percentOff) {
        if (percentOff == null || percentOff.compareTo(BigDecimal.ZERO) <= 0
                || percentOff.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("percentOff must be between 0 (exclusive) and 100 (inclusive)");
        }
        this.percentOff = percentOff;
    }

    public BigDecimal getPercentOff() { return percentOff; }

    @Override
    public BigDecimal priceAfterDiscount(ActiveOrder order, String couponCode, Instant systemClock) {
        BigDecimal original = order.getTotalPrice();
        BigDecimal multiplier = BigDecimal.ONE.subtract(percentOff.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        return original.multiply(multiplier).setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
    }
}
