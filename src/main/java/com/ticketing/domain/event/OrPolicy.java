package com.ticketing.domain.event;

import java.util.List;

public class OrPolicy implements IPurchasePolicy {
    private final List<IPurchasePolicy> policies;

    public OrPolicy(List<IPurchasePolicy> policies) {
        this.policies = policies == null ? List.of() : List.copyOf(policies);
    }

    public List<IPurchasePolicy> getPolicies() { return policies; }

    @Override
    public PolicyResult isAllowed(PurchaseContext context) {
        if (policies.isEmpty()) {
            return PolicyResult.success();
        }

        for (IPurchasePolicy policy : policies) {
            PolicyResult result = policy.isAllowed(context);
            if (result.allowed()) {
                return PolicyResult.success();
            }
        }
        return PolicyResult.failure("ALL_OR_CONDITIONS_FAILED", "No policies in the OR condition passed.");
    }
}
