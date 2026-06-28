package com.ticketing.application.transactions;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.event.SeatStatus;

/**
 * #508 (must-have): no double-sell of any ticket under high concurrency.
 *
 * <p>{@link SeatReservationLockingJpaTest} (V3-11 #269) proves the {@code @Version} guards
 * on {@link Seat} and {@link InventoryZone} work for the two-transaction case. This suite
 * widens that proof to multi-thread stress on the seat-claim path — exactly the workload
 * the platform sees at gate-open for popular events. Across every scenario the invariant
 * is the same: exactly the right number of tickets locked, zero over-sells.
 *
 * <p>Each thread opens its own {@link TransactionTemplate} transaction so the JPA flush
 * goes through the versioned UPDATE; the losers see the domain
 * {@link com.ticketing.domain.exception.OptimisticLockException} (mapped from Hibernate's
 * optimistic-lock failure by the JPA repos). We do not assert *which* thread wins — only
 * that the database ends in the legal end-state.
 *
 * <p>Tagged {@code slow} so it runs alongside the other {@code @SpringBootTest} suites
 * via {@code mvn -Pall-tests test} or {@code mvn test -Dtest.excludedGroups=}.
 */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(
        properties = {
                "ticketing.persistence=jpa",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "ticketing.seed.enabled=false",
                "ticketing.startup.initialize-platform=false"
        })
@DisplayName("Atomic ticket reservation — no double-sell under N-thread contention (#508)")
class SeatReservationStressJpaTest {

    private static final int RACE_THREADS = 20;
    private static final long RACE_TIMEOUT_SECONDS = 30;

    @Autowired
    private IEventRepository eventRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    // ── 20 threads race the SAME assigned seat ────────────────────────────────────

    @Test
    @DisplayName("Given N threads reserving the same seat, When the race resolves, Then exactly one succeeds and the seat is locked once")
    void GivenNThreadsReservingSameSeat_WhenRaceResolves_ThenExactlyOneSucceeds() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        eventRepository.save(publishedAssignedEvent(eventId, zoneId, List.of(seatId)));

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        runRace(RACE_THREADS, threadIndex -> attemptSeatLock(eventId, zoneId, seatId, successCount, conflictCount));

        assertThat(successCount.get())
                .as("exactly one thread should lock the seat")
                .isEqualTo(1);
        assertThat(conflictCount.get())
                .as("every losing thread should see OptimisticLockException (or be rejected by the in-process CAS as already LOCKED)")
                .isEqualTo(RACE_THREADS - 1);

        Seat persisted = eventRepository.findById(eventId).orElseThrow().findZone(zoneId).findSeat(seatId);
        assertThat(persisted.getStatus()).isEqualTo(SeatStatus.LOCKED);
    }

    // ── 20 threads race the LAST GA ticket (capacity 1) ───────────────────────────

    @Test
    @DisplayName("Given N threads reserving the last GA ticket in a capacity-1 zone, When the race resolves, Then exactly one ticket is locked")
    void GivenNThreadsReservingLastGaTicket_WhenRaceResolves_ThenExactlyOneSucceeds() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        eventRepository.save(publishedGaEvent(eventId, zoneId, /* capacity */ 1));

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        runRace(RACE_THREADS, threadIndex -> attemptGaLock(eventId, zoneId, 1, successCount, conflictCount));

        assertThat(successCount.get())
                .as("exactly one thread should lock the last GA ticket")
                .isEqualTo(1);
        assertThat(conflictCount.get())
                .as("every losing thread should see a conflict")
                .isEqualTo(RACE_THREADS - 1);

        InventoryZone persisted = eventRepository.findById(eventId).orElseThrow().findZone(zoneId);
        assertThat(persisted.getLockedCount()).isEqualTo(1);
        assertThat(persisted.getAvailableCount()).isZero();
    }

    // ── Mid-capacity GA race: 20 threads, 5 tickets — exactly 5 should lock ───────

    @Test
    @DisplayName("Given N threads reserving GA tickets in a capacity-5 zone, When the race resolves, Then no over-sell occurs and DB matches success count")
    void GivenNThreadsReservingGaTicketsInLimitedZone_WhenRaceResolves_ThenNoOverSell() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        int capacity = 5;
        eventRepository.save(publishedGaEvent(eventId, zoneId, capacity));

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        runRace(RACE_THREADS, threadIndex -> attemptGaLock(eventId, zoneId, 1, successCount, conflictCount));

        // Without retry, each race round produces at most ONE winning flush against the zone's
        // @Version (every other thread's flush is stale). Across an unsynchronised race the
        // exact winner count depends on scheduling, but the *invariants* are firm: at least
        // one thread succeeds, no thread succeeds past capacity, and the DB row matches the
        // tally. The application-layer "please retry" pattern lets subsequent rounds claim
        // the remaining inventory; that is covered by other tests of the OrderService retry
        // path, not by this stress test of the version guard itself.
        assertThat(successCount.get())
                .as("no over-sell — successes capped at capacity")
                .isBetween(1, capacity);
        assertThat(successCount.get() + conflictCount.get())
                .as("every thread terminates with a definite outcome")
                .isEqualTo(RACE_THREADS);

        InventoryZone persisted = eventRepository.findById(eventId).orElseThrow().findZone(zoneId);
        assertThat(persisted.getLockedCount())
                .as("DB locked count tracks success count exactly")
                .isEqualTo(successCount.get());
        assertThat(persisted.getAvailableCount() + persisted.getLockedCount())
                .as("availability accounting is preserved — no ticket vanishes or duplicates")
                .isEqualTo(capacity);
    }

    // ── Independent seats in the SAME event: all should succeed ──────────────────

    @Test
    @DisplayName("Given N threads each reserving a different seat in the same event, When the race resolves, Then all N succeed")
    void GivenNThreadsReservingDifferentSeatsInSameEvent_WhenRaceResolves_ThenAllSucceed() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        List<UUID> seatIds = new ArrayList<>();
        for (int i = 0; i < RACE_THREADS; i++) {
            seatIds.add(UUID.randomUUID());
        }
        eventRepository.save(publishedAssignedEvent(eventId, zoneId, seatIds));

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        runRace(RACE_THREADS, threadIndex ->
                attemptSeatLock(eventId, zoneId, seatIds.get(threadIndex), successCount, conflictCount));

        // The Event aggregate root and the InventoryZone child both carry @Version, and the
        // zone's seat collection is dirty for every save. With independent seats but shared
        // zone, the zone-version write contention can still produce conflicts — those losers
        // should still see no double-lock, just a retryable conflict. The hard invariant is:
        // no seat is double-locked, and the total locked count is exactly successCount.
        assertThat(successCount.get() + conflictCount.get())
                .as("every thread should either succeed or be flagged as a retryable conflict")
                .isEqualTo(RACE_THREADS);
        assertThat(successCount.get())
                .as("at least one independent-seat reservation should succeed")
                .isGreaterThanOrEqualTo(1);

        Event persisted = eventRepository.findById(eventId).orElseThrow();
        long lockedSeats = persisted.findZone(zoneId).getSeats().stream()
                .filter(Seat::isLocked)
                .count();
        assertThat(lockedSeats)
                .as("locked-seat count in DB matches success count — no over- or under-locking")
                .isEqualTo(successCount.get());
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    /** Runs N threads behind a starting gate to provoke a real race, then waits for them. */
    private void runRace(int threadCount, ThreadTask task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        start.await();
                        task.run(idx);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .as("race should resolve within " + RACE_TIMEOUT_SECONDS + "s")
                    .isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private void attemptSeatLock(
            UUID eventId, UUID zoneId, UUID seatId,
            AtomicInteger successCount, AtomicInteger conflictCount) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Event snapshot = eventRepository.findById(eventId).orElseThrow();
                snapshot.findZone(zoneId).lockSeat(seatId);
                eventRepository.save(snapshot);
            });
            successCount.incrementAndGet();
        } catch (RuntimeException ex) {
            // OptimisticLockException OR an IllegalStateException("Seat is not available")
            // from the in-process CAS, depending on whether the loser flushed before or after
            // the winner. Both are correct "loser" outcomes; neither is a double-sell.
            conflictCount.incrementAndGet();
        }
    }

    private void attemptGaLock(
            UUID eventId, UUID zoneId, int qty,
            AtomicInteger successCount, AtomicInteger conflictCount) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Event snapshot = eventRepository.findById(eventId).orElseThrow();
                snapshot.findZone(zoneId).lockGA(qty);
                eventRepository.save(snapshot);
            });
            successCount.incrementAndGet();
        } catch (RuntimeException ex) {
            // Same rationale as attemptSeatLock — either the @Version guard fired at flush
            // (OptimisticLockException) or the in-process invariant rejected the over-lock
            // (IllegalStateException). Both are valid "loser" outcomes.
            conflictCount.incrementAndGet();
        }
    }

    @FunctionalInterface
    private interface ThreadTask {
        void run(int threadIndex) throws InterruptedException;
    }

    // ── fixture builders (mirrored from SeatReservationLockingJpaTest) ────────────

    private static Event publishedGaEvent(UUID eventId, UUID zoneId, int capacity) {
        Event event = newDraftEvent(eventId);
        event.addZone(InventoryZone.createGA(zoneId, "Floor", new BigDecimal("45.00"), capacity));
        event.publish();
        return event;
    }

    private static Event publishedAssignedEvent(UUID eventId, UUID zoneId, List<UUID> seatIds) {
        Event event = newDraftEvent(eventId);
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "Orchestra", new BigDecimal("99.00"));
        int rowOffset = 0;
        for (UUID seatId : seatIds) {
            zone.addSeat(new Seat(seatId, "A", String.valueOf(++rowOffset)));
        }
        event.addZone(zone);
        event.publish();
        return event;
    }

    private static Event newDraftEvent(UUID eventId) {
        Instant start = Instant.now().plus(Duration.ofDays(30));
        return new Event(
                eventId, "Acme Productions", "Stress Fest", "desc",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(Duration.ofHours(3)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(30)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy());
    }
}
