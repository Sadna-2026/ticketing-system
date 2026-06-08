package com.ticketing.domain.event;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("ALWAYS_ALLOW")
public class AlwaysAllowPolicy extends AbstractPurchasePolicy {

    public AlwaysAllowPolicy() {
    }

    @Override
    public PolicyResult isAllowed(PurchaseContext context) {
        return PolicyResult.success();
    }
}
