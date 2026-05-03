package com.ticketing.domain.event;

import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.user.User;

import java.util.Currency;

public class NoDiscountPolicy implements IDiscountPolicy {
    private final Currency defaultCurrency;

    public NoDiscountPolicy(Currency defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    @Override
    public Money applyTo(ActiveOrder order, User user) {
        return Money.zero(defaultCurrency);
    }
}