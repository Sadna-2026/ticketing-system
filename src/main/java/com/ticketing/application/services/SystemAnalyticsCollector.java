package com.ticketing.application.services;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.ticketing.application.ISystemClock;

/**
 * In-memory system analytics counters (V3-9-C). Rates are derived from event timestamps
 * recorded at login, logout, registration, reservation and purchase flows.
 */
@Component
public class SystemAnalyticsCollector {

    static final Duration LIVE_WINDOW = Duration.ofMinutes(5);

    private final ISystemClock clock;
    private final Instant startedAt;
    private final Deque<Instant> visitorEnters = new ArrayDeque<>();
    private final Deque<Instant> visitorExits = new ArrayDeque<>();
    private final Deque<Instant> registrations = new ArrayDeque<>();
    private final Deque<Instant> reservations = new ArrayDeque<>();
    private final Deque<Instant> purchases = new ArrayDeque<>();
    private final AtomicInteger activeVisitors = new AtomicInteger(0);
    private final AtomicInteger totalVisitorEnters = new AtomicInteger(0);
    private final AtomicInteger totalVisitorExits = new AtomicInteger(0);
    private final AtomicInteger totalRegistrations = new AtomicInteger(0);
    private final AtomicInteger totalReservations = new AtomicInteger(0);
    private final AtomicInteger totalPurchases = new AtomicInteger(0);

    public SystemAnalyticsCollector(ISystemClock clock) {
        this.clock = clock;
        this.startedAt = clock.now();
    }

    public synchronized void recordVisitorEnter() {
        Instant now = clock.now();
        visitorEnters.addLast(now);
        totalVisitorEnters.incrementAndGet();
        activeVisitors.incrementAndGet();
        prune(visitorEnters, now);
    }

    public synchronized void recordVisitorExit() {
        Instant now = clock.now();
        visitorExits.addLast(now);
        totalVisitorExits.incrementAndGet();
        activeVisitors.updateAndGet(current -> Math.max(0, current - 1));
        prune(visitorExits, now);
    }

    public synchronized void recordRegistration() {
        Instant now = clock.now();
        registrations.addLast(now);
        totalRegistrations.incrementAndGet();
        prune(registrations, now);
    }

    public synchronized void recordReservation(int ticketCount) {
        if (ticketCount <= 0) {
            return;
        }
        Instant now = clock.now();
        for (int i = 0; i < ticketCount; i++) {
            reservations.addLast(now);
        }
        totalReservations.addAndGet(ticketCount);
        prune(reservations, now);
    }

    public synchronized void recordPurchase(int ticketCount) {
        if (ticketCount <= 0) {
            return;
        }
        Instant now = clock.now();
        for (int i = 0; i < ticketCount; i++) {
            purchases.addLast(now);
        }
        totalPurchases.addAndGet(ticketCount);
        prune(purchases, now);
    }

    public synchronized Snapshot snapshot() {
        Instant now = clock.now();
        prune(visitorEnters, now);
        prune(visitorExits, now);
        prune(registrations, now);
        prune(reservations, now);
        prune(purchases, now);

        Duration historicalWindow = Duration.between(startedAt, now);
        if (historicalWindow.isZero()) {
            historicalWindow = Duration.ofSeconds(1);
        }

        return new Snapshot(
                now,
                activeVisitors.get(),
                LIVE_WINDOW,
                historicalWindow,
                metricsForWindow(now, LIVE_WINDOW),
                historicalMetrics(historicalWindow));
    }

    private MetricSnapshot metricsForWindow(Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        return new MetricSnapshot(
                rate(countSince(visitorEnters, cutoff), window),
                rate(countSince(visitorExits, cutoff), window),
                rate(countSince(registrations, cutoff), window),
                rate(countSince(reservations, cutoff), window),
                rate(countSince(purchases, cutoff), window));
    }

    private MetricSnapshot historicalMetrics(Duration window) {
        return new MetricSnapshot(
                rate(totalVisitorEnters.get(), window),
                rate(totalVisitorExits.get(), window),
                rate(totalRegistrations.get(), window),
                rate(totalReservations.get(), window),
                rate(totalPurchases.get(), window));
    }

    private static RateSnapshot rate(long count, Duration window) {
        double minutes = Math.max(window.toMillis() / 60_000.0, 1.0 / 60.0);
        return new RateSnapshot(count, count / minutes);
    }

    private void prune(Deque<Instant> events, Instant now) {
        Instant cutoff = now.minus(LIVE_WINDOW);
        while (!events.isEmpty() && events.peekFirst().isBefore(cutoff)) {
            events.pollFirst();
        }
    }

    private static long countSince(Deque<Instant> events, Instant cutoff) {
        return events.stream().filter(instant -> !instant.isBefore(cutoff)).count();
    }

    /** Live and historical system analytics for platform administrators (II.6.5). */
    public record Snapshot(
            Instant generatedAt,
            int activeVisitors,
            Duration liveWindow,
            Duration historicalWindow,
            MetricSnapshot live,
            MetricSnapshot historical) {
    }

    /** Count and per-minute rate for a single analytics metric. */
    public record RateSnapshot(long count, double perMinute) {
    }

    /** Visitor, registration, reservation and purchase metrics for one time window. */
    public record MetricSnapshot(
            RateSnapshot visitorEnter,
            RateSnapshot visitorExit,
            RateSnapshot registration,
            RateSnapshot reservation,
            RateSnapshot purchase) {
    }
}
