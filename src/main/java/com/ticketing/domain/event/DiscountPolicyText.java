package com.ticketing.domain.event;

import java.util.ArrayList;
import java.util.List;

/**
 * User-facing wording for discount-policy trees.
 */
public final class DiscountPolicyText {

    private DiscountPolicyText() {
    }

    public static String describeVisibleDiscount(IDiscountPolicy policy) {
        if (policy == null || policy instanceof NoDiscountPolicy || policy instanceof CouponDiscount) {
            return "";
        }
        if (policy instanceof SimpleDiscount simple) {
            return percent(simple.getPercentOff()) + "% off all tickets";
        }
        if (policy instanceof ConditionalDiscount conditional) {
            return percent(conditional.getPercentOff()) + "% off when "
                    + describeCondition(conditional.getCondition());
        }
        if (policy instanceof MaxCompositeDiscount max) {
            return describeComposite("best of", max.getPolicies(), "OR");
        }
        if (policy instanceof SumCompositeDiscount sum) {
            return describeComposite("stacked", sum.getPolicies(), "AND");
        }
        return policy.getClass().getSimpleName();
    }

    public static String describeManagementDiscount(IDiscountPolicy policy) {
        if (policy == null || policy instanceof NoDiscountPolicy) {
            return "No discount";
        }
        if (policy instanceof CouponDiscount coupon) {
            return percent(coupon.getPercentOff()) + "% coupon '" + coupon.getCouponCode()
                    + "' until " + coupon.getExpiresAt();
        }
        String visible = describeVisibleDiscount(policy);
        return visible == null || visible.isBlank() ? policy.getClass().getSimpleName() : visible;
    }

    private static String describeComposite(String label, List<IDiscountPolicy> policies, String operator) {
        List<String> parts = new ArrayList<>();
        for (IDiscountPolicy child : policies) {
            String description = describeVisibleDiscount(child);
            if (description != null && !description.isBlank()) {
                parts.add(description);
            }
        }
        if (parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return label + ": (" + String.join(" " + operator + " ", parts) + ")";
    }

    private static String describeCondition(IDiscountCondition condition) {
        if (condition instanceof MinQuantityCondition min) {
            return "buying at least " + min.getMinTickets() + " tickets";
        }
        if (condition instanceof MaxQuantityCondition max) {
            return "buying at most " + max.getMaxTickets() + " tickets";
        }
        if (condition instanceof DateRangeCondition range) {
            String from = range.getFrom() == null ? "now" : range.getFrom().toString();
            String to = range.getTo() == null ? "event day" : range.getTo().toString();
            return "purchasing between " + from + " and " + to;
        }
        return "a qualifying purchase is made";
    }

    private static String percent(java.math.BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
