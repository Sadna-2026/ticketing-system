package com.ticketing.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ticketing.domain.order.ActiveOrder;

/**
 * "No stacking" — evaluates all child discounts and applies only the
 * largest one (the one producing the lowest final price).
 */
public class MaxCompositeDiscount implements IDiscountPolicy {

    private final List<IDiscountPolicy> policies;

    public MaxCompositeDiscount(List<IDiscountPolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            throw new IllegalArgumentException("At least one discount policy is required");
        }
        this.policies = List.copyOf(policies);
    }

    public List<IDiscountPolicy> getPolicies() { return policies; }

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
