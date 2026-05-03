package com.ticketing.domain.event;

import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.user.User;

public class AlwaysAllowPolicy implements IPurchasePolicy {
    @Override
    public PolicyResult isAllowed(ActiveOrder order, User user) {
        return PolicyResult.success();
    }
}