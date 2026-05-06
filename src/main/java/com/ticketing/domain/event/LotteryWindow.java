package com.ticketing.domain.event;

import java.time.Instant;

/**
 * The time window during which members may register for an event's
 * purchase-right lottery. Immutable value object.
 */
public record LotteryWindow(Instant registrationOpen, Instant registrationClose) {

    public LotteryWindow {
        if (registrationOpen == null) throw new IllegalArgumentException("registrationOpen is required");
        if (registrationClose == null) throw new IllegalArgumentException("registrationClose is required");
        if (!registrationOpen.isBefore(registrationClose)) {
            throw new IllegalArgumentException("registrationOpen must be before registrationClose");
        }
    }

    /**
     * Returns true when {@code now} falls within [registrationOpen, registrationClose).
     */
    public boolean isOpen(Instant now) {
        if (now == null) throw new IllegalArgumentException("now is required");
        return !now.isBefore(registrationOpen) && now.isBefore(registrationClose);
    }
}
