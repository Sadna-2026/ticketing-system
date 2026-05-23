package com.ticketing.domain.event;

import java.math.BigDecimal;
import java.time.Instant;

import com.ticketing.domain.order.ActiveOrder;

public class NoDiscountPolicy implements IDiscountPolicy {
    @Override
    public BigDecimal applyTo(ActiveOrder order, String couponCode, Instant systemClock) {
        return BigDecimal.ZERO;
    }
}
