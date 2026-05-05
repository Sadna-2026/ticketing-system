package com.ticketing.domain.event;

import java.util.List;
import java.util.UUID;

import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.infrastructure.Interface.IPurchasePolicy;

public class OrPolicy implements IPurchasePolicy {
    private final List<IPurchasePolicy> policies;

    public OrPolicy(List<IPurchasePolicy> policies) {
        this.policies = policies == null ? List.of() : List.copyOf(policies);
    }

    @Override
    public PolicyResult isAllowed(ActiveOrder order, UUID memberId) {
        if (policies.isEmpty()) {
            return PolicyResult.success();
        }

        for (IPurchasePolicy policy : policies) {
            PolicyResult result = policy.isAllowed(order, memberId);
            if (result.allowed()) {
                return PolicyResult.success();
            }
        }
        return PolicyResult.failure("ALL_OR_CONDITIONS_FAILED", "No policies in the OR condition passed.");
    }
}
