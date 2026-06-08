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
 * "Stacking" — evaluates all child discounts and accumulates every
 * discount amount. The total discount is subtracted from the original price.
 *
 * <p>The child list keeps the {@link IDiscountPolicy} element type so any discount
 * (including ad-hoc lambdas in tests) can be composed at runtime. For persistence the
 * relationship targets the {@link AbstractDiscountPolicy} entity base via a
 * {@code @OneToMany} join table; only entity children are persisted/reloaded. {@code final}
 * was removed from {@code policies} so Hibernate can rehydrate the collection.
 */
@Entity
@DiscriminatorValue("SUM_COMPOSITE")
public class SumCompositeDiscount extends AbstractDiscountPolicy {

    @OneToMany(targetEntity = AbstractDiscountPolicy.class,
            cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinTable(name = "sum_composite_children",
            joinColumns = @JoinColumn(name = "parent_id"),
            inverseJoinColumns = @JoinColumn(name = "child_id"))
    private List<IDiscountPolicy> policies;

    // Required by JPA; do not use directly.
    protected SumCompositeDiscount() {
        this.policies = new ArrayList<>();
    }

    public SumCompositeDiscount(List<IDiscountPolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            throw new IllegalArgumentException("At least one discount policy is required");
        }
        this.policies = new ArrayList<>(policies);
    }

    public List<IDiscountPolicy> getPolicies() { return List.copyOf(policies); }

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
