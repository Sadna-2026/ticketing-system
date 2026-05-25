package com.ticketing.domain.event;

import java.math.BigDecimal;

import com.ticketing.domain.order.ActiveOrder;


public interface IDiscountPolicy {
    BigDecimal priceAfterDiscount(ActiveOrder order, 
                    String couponCode, 
                    java.time.Instant systemClock);
}

