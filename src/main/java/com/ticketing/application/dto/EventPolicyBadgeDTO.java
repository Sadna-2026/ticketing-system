package com.ticketing.application.dto;

/**
 * Buyer-facing summary of a purchase restriction or visible discount on an event.
 * Coupon-based discounts are intentionally omitted from this model.
 */
public record EventPolicyBadgeDTO(
        Kind kind,
        String title,
        String detail
) {
    public enum Kind {
        RESTRICTION,
        DISCOUNT
    }
}
