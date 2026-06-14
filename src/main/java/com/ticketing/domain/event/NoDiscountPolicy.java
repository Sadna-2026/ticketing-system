package com.ticketing.domain.event;

import java.math.BigDecimal;
import java.time.Instant;

import com.ticketing.domain.order.ActiveOrder;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("NONE")
public class NoDiscountPolicy extends AbstractDiscountPolicy {

    public NoDiscountPolicy() {
    }

    @Override
    public BigDecimal priceAfterDiscount(ActiveOrder order, String couponCode, Instant systemClock) {
        BigDecimal finalPrice = order.getTotalPrice();
        return finalPrice.max(BigDecimal.ZERO);
    }
}
