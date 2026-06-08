package com.ticketing.domain.order;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Value Object: Immutable snapshot of buyer contact info at time of purchase.
 * For anonymous guests, all fields may be null/empty.
 * For members, captures their contact details at purchase time.
 *
 * <p>Annotated {@code @Embeddable} for JPA readiness. In V3 this is purely a transient
 * checkout helper (computed in {@code OrderService.buyerContactFor} and threaded through
 * the payment/supply gateways); it is NOT a stored field on any persisted entity, so it
 * is not {@code @Embedded} anywhere yet. The annotation lets a future entity embed it
 * without a further migration. {@code final} was removed from the class and its fields,
 * and a protected no-arg constructor was added, as JPA requires for embeddables.
 */
@Embeddable
public class BuyerContactSnapshot {

    @Column(name = "buyer_email")
    private String email;
    @Column(name = "buyer_username")
    private String username;
    @Column(name = "buyer_phone_number")
    private String phoneNumber;

    /**
     * Creates an empty snapshot for anonymous guests.
     */
    public static BuyerContactSnapshot empty() {
        return new BuyerContactSnapshot(null, null, null);
    }

    // Required by JPA; do not use directly.
    protected BuyerContactSnapshot() {
    }

    public BuyerContactSnapshot(String email, String username, String phoneNumber) {
        this.email = email;
        this.username = username;
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getPhoneNumber() { return phoneNumber; }

    public boolean isEmpty() {
        return email == null && username == null && phoneNumber == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuyerContactSnapshot that = (BuyerContactSnapshot) o;
        return Objects.equals(email, that.email) &&
               Objects.equals(username, that.username) &&
               Objects.equals(phoneNumber, that.phoneNumber) &&
               Objects.equals(phoneNumber, that.phoneNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, username, phoneNumber);
    }
}
