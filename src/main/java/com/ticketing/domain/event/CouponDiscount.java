package com.ticketing.domain.event;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import com.ticketing.domain.order.ActiveOrder;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * A percentage discount activated by a coupon code. The code must match exactly
 * (case-insensitive) and the coupon must not be expired at purchase time.
 * If the code is wrong or expired, the original price is returned.
 */
@Entity
@DiscriminatorValue("COUPON")
public class CouponDiscount extends AbstractDiscountPolicy {

    @Column(name = "percent_off")
    private BigDecimal percentOff;
    @Column(name = "coupon_code")
    private String couponCode;
    @Column(name = "expires_at")
    private Instant expiresAt;

    // Required by JPA; do not use directly.
    protected CouponDiscount() {
    }

    public CouponDiscount(BigDecimal percentOff, String couponCode, Instant expiresAt) {
        if (percentOff == null || percentOff.compareTo(BigDecimal.ZERO) <= 0
                || percentOff.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("percentOff must be between 0 (exclusive) and 100 (inclusive)");
        }
        if (couponCode == null || couponCode.isBlank()) {
            throw new IllegalArgumentException("couponCode is required");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required");
        }
        this.percentOff = percentOff;
        this.couponCode = couponCode.trim();
        this.expiresAt = expiresAt;
    }

    public BigDecimal getPercentOff() { return percentOff; }
    public String getCouponCode() { return couponCode; }
    public Instant getExpiresAt() { return expiresAt; }

    /**
     * Returns why the coupon was not applied, or {@code null} if it would be applied.
     * <ul>
     *   <li>{@code "expired"}  – code matches but the coupon has passed its expiry date.</li>
     *   <li>{@code "invalid"}  – code does not match this coupon at all.</li>
     *   <li>{@code null}       – code matches and coupon is still valid; discount applies.</li>
     * </ul>
     */
    public String getRejectionReason(String couponCode, Instant systemClock) {
        if (couponCode == null || !this.couponCode.equalsIgnoreCase(couponCode.trim())) {
            return "invalid";
        }
        if (systemClock.isAfter(expiresAt)) {
            return "expired";
        }
        return null;
    }

    @Override
    public BigDecimal priceAfterDiscount(ActiveOrder order, String couponCode, Instant systemClock) {
        if (couponCode == null || !this.couponCode.equalsIgnoreCase(couponCode.trim())) {
            return order.getTotalPrice();
        }
        if (systemClock.isAfter(expiresAt)) {
            return order.getTotalPrice();
        }
        BigDecimal multiplier = BigDecimal.ONE.subtract(percentOff.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        return order.getTotalPrice().multiply(multiplier).setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
    }
}
