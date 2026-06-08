package com.ticketing.domain.event;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import com.ticketing.domain.order.ActiveOrder;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * A percentage discount that only applies when a condition (predicate) is met.
 * If the condition is not met, the original price is returned unchanged.
 * Example: "10% off when buying 2+ tickets", "15% off until May 15."
 *
 * <p>The {@code condition} keeps the {@link IDiscountCondition} type so an ad-hoc lambda
 * can still be supplied at runtime. For persistence it maps as a {@code @ManyToOne} to the
 * {@link AbstractDiscountCondition} entity base; only an entity condition is persisted/
 * reloaded. {@code final} was removed from both fields so Hibernate can set them.
 */
@Entity
@DiscriminatorValue("CONDITIONAL")
public class ConditionalDiscount extends AbstractDiscountPolicy {

    @Column(name = "percent_off")
    private BigDecimal percentOff;

     @ManyToOne(targetEntity = AbstractDiscountCondition.class, cascade = CascadeType.ALL)
    @JoinColumn(name = "condition_id")
    private IDiscountCondition condition;

    // Required by JPA; do not use directly.
    protected ConditionalDiscount() {
    }

    public ConditionalDiscount(BigDecimal percentOff, IDiscountCondition condition) {
        if (percentOff == null || percentOff.compareTo(BigDecimal.ZERO) <= 0
                || percentOff.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("percentOff must be between 0 (exclusive) and 100 (inclusive)");
        }
        if (condition == null) throw new IllegalArgumentException("condition is required");
        this.percentOff = percentOff;
        this.condition = condition;
    }

    public BigDecimal getPercentOff() { return percentOff; }
    public IDiscountCondition getCondition() { return condition; }

    @Override
    public BigDecimal priceAfterDiscount(ActiveOrder order, String couponCode, Instant systemClock) {
        if (!condition.isMet(order, systemClock)) {
            return order.getTotalPrice();
        }
        BigDecimal multiplier = BigDecimal.ONE.subtract(percentOff.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        return order.getTotalPrice().multiply(multiplier).setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
    }
}
