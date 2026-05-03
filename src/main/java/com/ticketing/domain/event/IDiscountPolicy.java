package com.ticketing.domain.event;

import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.user.User;

public interface IDiscountPolicy {
    Money applyTo(ActiveOrder order, User user);
}