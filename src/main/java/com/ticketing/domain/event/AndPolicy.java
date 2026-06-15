package com.ticketing.domain.event;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;

/**
 * Composite purchase policy: allowed only if every child policy allows.
 *
 * <p>The child list keeps the {@link IPurchasePolicy} element type so any policy
 * (including ad-hoc lambdas used in tests) can be composed at runtime. For persistence
 * the relationship targets the {@link AbstractPurchasePolicy} entity base via a
 * {@code @OneToMany} join table; only entity children are persisted/reloaded, which is
 * all the JPA round-trip ever holds. {@code final} was removed from {@code policies} so
 * Hibernate can rehydrate the collection.
 */
@Entity
@DiscriminatorValue("AND")
public class AndPolicy extends AbstractPurchasePolicy {

    @OneToMany(targetEntity = AbstractPurchasePolicy.class,
            cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinTable(name = "and_policy_children",
            joinColumns = @jakarta.persistence.JoinColumn(name = "parent_id"),
            inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "child_id"))
    private List<IPurchasePolicy> policies;

    // Required by JPA; do not use directly.
    protected AndPolicy() {
        this.policies = new ArrayList<>();
    }

    public AndPolicy(List<IPurchasePolicy> policies) {
        this.policies = policies == null ? new ArrayList<>() : new ArrayList<>(policies);
    }

    public List<IPurchasePolicy> getPolicies() { return List.copyOf(policies); }

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

    @Override
    public List<String> collectViolations(PurchaseContext context) {
        List<String> violations = new ArrayList<>();
        for (IPurchasePolicy policy : policies) {
            violations.addAll(policy.collectViolations(context));
        }
        return violations;
    }
}
