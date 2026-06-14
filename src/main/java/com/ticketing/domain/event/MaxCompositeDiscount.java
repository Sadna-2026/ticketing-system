package com.ticketing.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.ticketing.domain.order.ActiveOrder;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;

/**
 * "No stacking" — evaluates all child discounts and applies only the
 * largest one (the one producing the lowest final price).
 *
 * <p>The child list keeps the {@link IDiscountPolicy} element type so any discount
 * (including ad-hoc lambdas in tests) can be composed at runtime. For persistence the
 * relationship targets the {@link AbstractDiscountPolicy} entity base via a
 * {@code @OneToMany} join table; only entity children are persisted/reloaded. {@code final}
 * was removed from {@code policies} so Hibernate can rehydrate the collection.
 */
@Entity
@DiscriminatorValue("MAX_COMPOSITE")
public class MaxCompositeDiscount extends AbstractDiscountPolicy {

    @OneToMany(targetEntity = AbstractDiscountPolicy.class,
            cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinTable(name = "max_composite_children",
            joinColumns = @JoinColumn(name = "parent_id"),
            inverseJoinColumns = @JoinColumn(name = "child_id"))
    private List<IDiscountPolicy> policies;

    // Required by JPA; do not use directly.
    protected MaxCompositeDiscount() {
        this.policies = new ArrayList<>();
    }

    public MaxCompositeDiscount(List<IDiscountPolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            throw new IllegalArgumentException("At least one discount policy is required");
        }
        this.policies = new ArrayList<>(policies);
    }

    public List<IDiscountPolicy> getPolicies() { return List.copyOf(policies); }

    @Override
    public BigDecimal priceAfterDiscount(ActiveOrder order, String couponCode, Instant systemClock) {
        BigDecimal best = order.getTotalPrice();
        for (IDiscountPolicy policy : policies) {
            BigDecimal candidate = policy.priceAfterDiscount(order, couponCode, systemClock);
            if (candidate.compareTo(best) < 0) {
                best = candidate;
            }
        }
        return best.max(BigDecimal.ZERO);
    }
}
