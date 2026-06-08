package com.ticketing.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ticketing.domain.order.ActiveOrder;

/**
 * "Stacking" — evaluates all child discounts and accumulates every
 * discount amount. The total discount is subtracted from the original price.
 */
public class SumCompositeDiscount implements IDiscountPolicy {

    private final List<IDiscountPolicy> policies;

    public SumCompositeDiscount(List<IDiscountPolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            throw new IllegalArgumentException("At least one discount policy is required");
        }
        this.policies = List.copyOf(policies);
    }

    public List<IDiscountPolicy> getPolicies() { return policies; }

    @Override
    public BigDecimal priceAfterDiscount(ActiveOrder order, String couponCode, Instant systemClock) {
        BigDecimal originalPrice = order.getTotalPrice();
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (IDiscountPolicy policy : policies) {
            BigDecimal childResult = policy.priceAfterDiscount(order, couponCode, systemClock);
            BigDecimal childDiscount = originalPrice.subtract(childResult);
            if (childDiscount.compareTo(BigDecimal.ZERO) > 0) {
                totalDiscount = totalDiscount.add(childDiscount);
            }
        }

        return originalPrice.subtract(totalDiscount).max(BigDecimal.ZERO);
    }
}
