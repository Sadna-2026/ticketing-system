package com.ticketing.domain.event;

import java.util.List;

public interface IPurchasePolicy {
    PolicyResult isAllowed(PurchaseContext context);

    /** Collects every violation reason; composite policies aggregate their children. */
    default List<String> collectViolations(PurchaseContext context) {
        PolicyResult result = isAllowed(context);
        if (result.allowed()) {
            return List.of();
        }
        String reason = result.reason();
        if (reason == null || reason.isBlank()) {
            return List.of();
        }
        return List.of(reason);
    }
}

