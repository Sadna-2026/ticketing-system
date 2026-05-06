package com.ticketing.domain.order;

import java.util.Objects;

/**
 * Value Object: Immutable snapshot of buyer contact info at time of purchase.
 * For anonymous guests, all fields may be null/empty.
 * For members, captures their contact details at purchase time.
 */
public final class BuyerContactSnapshot {

    private final String email;
    private final String username;
    private final String phoneNumber;

    /**
     * Creates an empty snapshot for anonymous guests.
     */
    public static BuyerContactSnapshot empty() {
        return new BuyerContactSnapshot(null, null, null);
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
