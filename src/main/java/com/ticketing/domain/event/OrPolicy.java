package com.ticketing.domain.event;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;

/**
 * Composite purchase policy: allowed if any child policy allows (an empty OR is allowed).
 *
 * <p>The child list keeps the {@link IPurchasePolicy} element type so any policy
 * (including ad-hoc lambdas used in tests) can be composed at runtime. For persistence
 * the relationship targets the {@link AbstractPurchasePolicy} entity base via a
 * {@code @OneToMany} join table; only entity children are persisted/reloaded. {@code final}
 * was removed from {@code policies} so Hibernate can rehydrate the collection.
 */
@Entity
@DiscriminatorValue("OR")
public class OrPolicy extends AbstractPurchasePolicy {

    @OneToMany(targetEntity = AbstractPurchasePolicy.class,
            cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinTable(name = "or_policy_children",
            joinColumns = @JoinColumn(name = "parent_id"),
            inverseJoinColumns = @JoinColumn(name = "child_id"))
    private List<IPurchasePolicy> policies;

    // Required by JPA; do not use directly.
    protected OrPolicy() {
        this.policies = new ArrayList<>();
    }

    public OrPolicy(List<IPurchasePolicy> policies) {
        this.policies = policies == null ? new ArrayList<>() : new ArrayList<>(policies);
    }

    public List<IPurchasePolicy> getPolicies() { return List.copyOf(policies); }

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
        List<String> violations = branchViolations(context);
        return PolicyResult.failure("ALL_OR_CONDITIONS_FAILED", String.join("\n", violations));
    }

    @Override
    public List<String> collectViolations(PurchaseContext context) {
        if (policies.isEmpty()) {
            return List.of();
        }
        for (IPurchasePolicy policy : policies) {
            if (policy.isAllowed(context).allowed()) {
                return List.of();
            }
        }
        return branchViolations(context);
    }

    /** When every OR branch fails, surface each branch's own violation text. */
    private List<String> branchViolations(PurchaseContext context) {
        List<String> violations = new ArrayList<>();
        for (IPurchasePolicy policy : policies) {
            List<String> childViolations = policy.collectViolations(context);
            if (!childViolations.isEmpty()) {
                violations.addAll(childViolations);
                continue;
            }
            PolicyResult result = policy.isAllowed(context);
            if (!result.allowed()) {
                String reason = result.reason();
                if (reason != null && !reason.isBlank()) {
                    violations.add(reason);
                }
            }
        }
        if (violations.isEmpty()) {
            return List.of("No policies in the OR condition passed.");
        }
        return violations;
    }
}
