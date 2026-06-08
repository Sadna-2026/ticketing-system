package com.ticketing.domain.event;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The time window during which members may register for an event's
 * purchase-right lottery. Immutable value object.
 *
 * <p>Mapped as an {@code @Embeddable} embedded (nullable — only present for LOTTERY
 * events) in {@link Event}. The {@code Instant} columns carry an explicit
 * {@code lottery_*} prefix so they do not collide with the similarly-named instants on
 * {@link EventSchedule}. Was a {@code record} in V2; converted to a class because JPA
 * cannot instantiate a record (no no-arg constructor / final components). The
 * record-style accessors ({@link #registrationOpen()}, {@link #registrationClose()})
 * and {@code equals}/{@code hashCode} are preserved so the public API is unchanged.
 */
@Embeddable
public class LotteryWindow {

    @Column(name = "lottery_registration_open")
    private Instant registrationOpen;
    @Column(name = "lottery_registration_close")
    private Instant registrationClose;

    // Required by JPA; do not use directly.
    protected LotteryWindow() {
    }

    public LotteryWindow(Instant registrationOpen, Instant registrationClose) {
        if (registrationOpen == null) throw new IllegalArgumentException("registrationOpen is required");
        if (registrationClose == null) throw new IllegalArgumentException("registrationClose is required");
        if (!registrationOpen.isBefore(registrationClose)) {
            throw new IllegalArgumentException("registrationOpen must be before registrationClose");
        }
        this.registrationOpen = registrationOpen;
        this.registrationClose = registrationClose;
    }

    public Instant registrationOpen() { return registrationOpen; }
    public Instant registrationClose() { return registrationClose; }

    /**
     * Returns true when {@code now} falls within [registrationOpen, registrationClose).
     */
    public boolean isOpen(Instant now) {
        if (now == null) throw new IllegalArgumentException("now is required");
        return !now.isBefore(registrationOpen) && now.isBefore(registrationClose);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LotteryWindow that = (LotteryWindow) o;
        return Objects.equals(registrationOpen, that.registrationOpen)
                && Objects.equals(registrationClose, that.registrationClose);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registrationOpen, registrationClose);
    }

    @Override
    public String toString() {
        return "LotteryWindow[registrationOpen=" + registrationOpen
                + ", registrationClose=" + registrationClose + "]";
    }
}
