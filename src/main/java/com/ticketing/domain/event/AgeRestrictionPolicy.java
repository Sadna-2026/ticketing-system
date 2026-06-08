package com.ticketing.domain.event;

import java.time.LocalDate;
import java.time.Period;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Rejects purchases when the buyer is below the required minimum age.
 * Guest purchases (null dateOfBirth) are rejected — age cannot be verified.
 */
@Entity
@DiscriminatorValue("AGE_RESTRICTION")
public class AgeRestrictionPolicy extends AbstractPurchasePolicy {

    @Column(name = "minimum_age")
    private int minimumAge;

    // Required by JPA; do not use directly.
    protected AgeRestrictionPolicy() {
    }

    public AgeRestrictionPolicy(int minimumAge) {
        if (minimumAge < 0) throw new IllegalArgumentException("minimumAge cannot be negative");
        this.minimumAge = minimumAge;
    }

    public int getMinimumAge() { return minimumAge; }

    @Override
    public PolicyResult isAllowed(PurchaseContext context) {
        LocalDate dob = context.buyerDateOfBirth();
        if (dob == null) {
            return PolicyResult.failure("AGE_UNKNOWN",
                    "Date of birth is required to verify age restriction (minimum " + minimumAge + ")");
        }
        int age = Period.between(dob, LocalDate.now()).getYears();
        if (age < minimumAge) {
            return PolicyResult.failure("AGE_RESTRICTED",
                    "You must be at least " + minimumAge + " years old to purchase tickets for this event");
        }
        return PolicyResult.success();
    }
}
