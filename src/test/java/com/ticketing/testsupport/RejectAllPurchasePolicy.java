package com.ticketing.testsupport;

import com.ticketing.domain.event.AbstractPurchasePolicy;
import com.ticketing.domain.event.PolicyResult;
import com.ticketing.domain.event.PurchaseContext;

/** Test-only purchase policy that always rejects with a configurable error code. */
public final class RejectAllPurchasePolicy extends AbstractPurchasePolicy {

    private final String errorCode;
    private final String message;

    public RejectAllPurchasePolicy(String errorCode, String message) {
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        this.errorCode = errorCode;
        this.message = message;
    }

    @Override
    public PolicyResult isAllowed(PurchaseContext context) {
        return PolicyResult.failure(errorCode, message);
    }
}
