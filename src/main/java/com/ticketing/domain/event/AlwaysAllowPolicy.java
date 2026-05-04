package com.ticketing.domain.event;


import com.ticketing.domain.member.User;
import com.ticketing.domain.order.ActiveOrder;

public class AlwaysAllowPolicy implements IPurchasePolicy {
    @Override
    public PolicyResult isAllowed(ActiveOrder order, User user) {
        return PolicyResult.success();
    }
}