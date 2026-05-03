package com.ticketing.domain.event;

import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.user.User;

import java.util.List;

public class AndPolicy implements IPurchasePolicy {
    private final List<IPurchasePolicy> policies;

    public AndPolicy(List<IPurchasePolicy> policies) {
        this.policies = policies;
    }

    @Override
    public PolicyResult isAllowed(ActiveOrder order, User user) {
        for (IPurchasePolicy policy : policies) {
            PolicyResult result = policy.isAllowed(order, user);
            if (!result.allowed()) {
                return result;
            }
        }
        return PolicyResult.success();
    }
}