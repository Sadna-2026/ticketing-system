package com.ticketing.domain.event;

import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.user.User;

import java.util.List;

public class OrPolicy implements IPurchasePolicy {
    private final List<IPurchasePolicy> policies;

    public OrPolicy(List<IPurchasePolicy> policies) {
        this.policies = policies;
    }

    @Override
    public PolicyResult isAllowed(ActiveOrder order, User user) {
        if (policies.isEmpty()) {
            return PolicyResult.success();
        }
        
        for (IPurchasePolicy policy : policies) {
            PolicyResult result = policy.isAllowed(order, user);
            if (result.allowed()) {
                return PolicyResult.success();
            }
        }
        return PolicyResult.failure("ALL_OR_CONDITIONS_FAILED", "No policies in the OR condition passed.");
    }
}