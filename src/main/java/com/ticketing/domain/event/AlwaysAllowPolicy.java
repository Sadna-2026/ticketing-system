package com.ticketing.domain.event;

public class AlwaysAllowPolicy implements IPurchasePolicy {
    @Override
    public PolicyResult isAllowed(PurchaseContext context) {
        return PolicyResult.success();
    }
}
