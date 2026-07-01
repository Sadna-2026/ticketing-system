package com.ticketing.domain.event;

import java.time.Duration;
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
    /** How many registrants are selected as winners when the draw runs. Default 50. */
    @Column(name = "lottery_max_winners", columnDefinition = "INT DEFAULT 50")
    private Integer maxWinners;
    /** Hours that winners have to purchase before tickets open to all. Default 48. */
    @Column(name = "lottery_purchase_window_hours", columnDefinition = "INT DEFAULT 48")
    private Integer purchaseWindowHours;
    /**
     * Optional finer-grained override of {@link #purchaseWindowHours}: when set, winners
     * have exactly this many minutes to purchase (minimum 1). Null means fall back to the
     * hour value. Lets a manager configure very short windows (e.g. 1 minute) — useful for
     * exercising the winner-window-expiry flow without waiting whole hours.
     */
    @Column(name = "lottery_purchase_window_minutes")
    private Integer purchaseWindowMinutes;

    // Required by JPA; do not use directly.
    protected LotteryWindow() {
    }

    public LotteryWindow(Instant registrationOpen, Instant registrationClose, int maxWinners, int purchaseWindowHours) {
        if (registrationOpen == null) throw new IllegalArgumentException("registrationOpen is required");
        if (registrationClose == null) throw new IllegalArgumentException("registrationClose is required");
        if (!registrationOpen.isBefore(registrationClose)) {
            throw new IllegalArgumentException("registrationOpen must be before registrationClose");
        }
        if (maxWinners < 1) throw new IllegalArgumentException("maxWinners must be at least 1");
        if (purchaseWindowHours < 1) throw new IllegalArgumentException("purchaseWindowHours must be at least 1");
        this.registrationOpen = registrationOpen;
        this.registrationClose = registrationClose;
        this.maxWinners = maxWinners;
        this.purchaseWindowHours = purchaseWindowHours;
    }

    /**
     * As the hours-based constructor, but with an optional minute-level override for the
     * winner purchase window. Pass {@code null} for {@code purchaseWindowMinutes} to keep
     * the hour-based window.
     */
    public LotteryWindow(Instant registrationOpen, Instant registrationClose, int maxWinners,
                         int purchaseWindowHours, Integer purchaseWindowMinutes) {
        this(registrationOpen, registrationClose, maxWinners, purchaseWindowHours);
        if (purchaseWindowMinutes != null && purchaseWindowMinutes < 1) {
            throw new IllegalArgumentException("purchaseWindowMinutes must be at least 1");
        }
        this.purchaseWindowMinutes = purchaseWindowMinutes;
    }

    /** Backward-compatible constructor — defaults to 50 winners and 48-hour purchase window. */
    public LotteryWindow(Instant registrationOpen, Instant registrationClose) {
        this(registrationOpen, registrationClose, 50, 48);
    }

    public Instant registrationOpen() { return registrationOpen; }
    public Instant registrationClose() { return registrationClose; }
    public int maxWinners() { return maxWinners != null ? maxWinners : 50; }
    public int purchaseWindowHours() { return purchaseWindowHours != null ? purchaseWindowHours : 48; }
    /** The minute-level override, or {@code null} when the window is hour-based. */
    public Integer purchaseWindowMinutes() { return purchaseWindowMinutes; }

    /**
     * The effective winner purchase window as a {@link Duration}: the minute override when
     * present, otherwise the hour value. This is the single source of truth callers should
     * use when computing a winner's deadline.
     */
    public Duration purchaseWindowDuration() {
        return purchaseWindowMinutes != null
                ? Duration.ofMinutes(purchaseWindowMinutes)
                : Duration.ofHours(purchaseWindowHours());
    }

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
                && Objects.equals(registrationClose, that.registrationClose)
                && Objects.equals(maxWinners, that.maxWinners)
                && Objects.equals(purchaseWindowHours, that.purchaseWindowHours)
                && Objects.equals(purchaseWindowMinutes, that.purchaseWindowMinutes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registrationOpen, registrationClose, maxWinners, purchaseWindowHours,
                purchaseWindowMinutes);
    }

    @Override
    public String toString() {
        return "LotteryWindow[registrationOpen=" + registrationOpen
                + ", registrationClose=" + registrationClose
                + ", maxWinners=" + maxWinners
                + ", purchaseWindowHours=" + purchaseWindowHours
                + ", purchaseWindowMinutes=" + purchaseWindowMinutes + "]";
    }
}
