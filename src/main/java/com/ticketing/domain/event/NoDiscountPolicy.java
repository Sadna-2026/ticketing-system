package com.ticketing.domain.event;

import java.math.BigDecimal;
import java.time.Instant;

import com.ticketing.domain.order.ActiveOrder;

public class NoDiscountPolicy implements IDiscountPolicy {
    @Override
    public BigDecimal priceAfterDiscount(ActiveOrder order, String couponCode, Instant systemClock) {
        BigDecimal finalPrice = order.getTotalPrice();
        return finalPrice.max(BigDecimal.ZERO);
    }
}
