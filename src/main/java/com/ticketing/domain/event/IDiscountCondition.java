package com.ticketing.domain.event;

import java.time.Instant;

import com.ticketing.domain.order.ActiveOrder;

/**
 * Predicate for conditional discounts. Implementations decide whether
 * a discount should apply based on order state or timing.
 * Extensible — add new conditions without modifying existing ones.
 */
public interface IDiscountCondition {
    boolean isMet(ActiveOrder order, Instant now);
}
