package com.ticketing.domain.event;

import java.util.List;
import java.util.UUID;

import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.infrastructure.Interface.IPurchasePolicy;

public class AndPolicy implements IPurchasePolicy {
    private final List<IPurchasePolicy> policies;

    public AndPolicy(List<IPurchasePolicy> policies) {
        this.policies = policies == null ? List.of() : List.copyOf(policies);
    }

    @Override
    public PolicyResult isAllowed(ActiveOrder order, UUID memberId) {
        for (IPurchasePolicy policy : policies) {
            PolicyResult result = policy.isAllowed(order, memberId);
            if (!result.allowed()) {
                return result;
            }
        }
        return PolicyResult.success();
    }
}
