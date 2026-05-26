package com.ticketing.domain.event;

public interface IPurchasePolicy {
    PolicyResult isAllowed(PurchaseContext context);
}

