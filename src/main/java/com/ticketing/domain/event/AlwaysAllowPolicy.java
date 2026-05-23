package com.ticketing.domain.event;

import java.util.UUID;

import com.ticketing.domain.order.ActiveOrder;

public class AlwaysAllowPolicy implements IPurchasePolicy {
    @Override
    public PolicyResult isAllowed(ActiveOrder order, UUID memberId) {
        return PolicyResult.success();
    }
}
