package com.ticketing.infrastructure.Interface;

import com.ticketing.domain.event.Money;


import com.ticketing.domain.member.User;
import com.ticketing.domain.order.ActiveOrder;


public interface IDiscountPolicy {
    Money applyTo(ActiveOrder order, User mockUser);
}

