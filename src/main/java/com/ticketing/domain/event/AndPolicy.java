package com.ticketing.domain.event;

import java.util.List;

public class AndPolicy implements IPurchasePolicy {
    private final List<IPurchasePolicy> policies;

    public AndPolicy(List<IPurchasePolicy> policies) {
        this.policies = policies == null ? List.of() : List.copyOf(policies);
    }

    public List<IPurchasePolicy> getPolicies() { return policies; }

    @Override
    public PolicyResult isAllowed(PurchaseContext context) {
        for (IPurchasePolicy policy : policies) {
            PolicyResult result = policy.isAllowed(context);
            if (!result.allowed()) {
                return result;
            }
        }
        return PolicyResult.success();
    }
}
