package com.ticketing.application.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

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
import com.ticketing.domain.exception.OptimisticLockException;

/**
 * V3-11 (#269): JPA optimistic locking on the inventory children of the Event aggregate.
 *
 * <p>#268 added a JPA {@code @Version} to the Event ROOT and the ActiveOrder, but the
 * inventory counts that actually get double-sold live on the CHILD entities — seat
 * {@code status} on {@link Seat}, GA {@code available/locked/sold} on
 * {@link InventoryZone}. Mutating a child does NOT dirty the parent Event, so its root
 * {@code @Version} never moved and two concurrent reservations of the same seat / the
 * last GA ticket both committed → double-sell.
 *
 * <p>#269 fixes this by putting a {@code @Version} on {@link Seat} and
 * {@link InventoryZone} themselves. The save path is
 * {@code eventRepository.save(event)} → {@link
 * com.ticketing.infrastructure.persistence.JpaEventRepository#save} →
 * {@code entityManager.merge(event)}, and the Event→zones→seats graph is
 * {@code cascade=ALL}, so the merge cascades to the dirty child and issues a
 * <em>versioned</em> UPDATE for it. When two transactions each loaded an independent
 * detached snapshot (the repo {@code detach()}es every read) and both mutate the same
 * child, the first commit bumps the child's version; the second merge carries the now
 * stale child version and Hibernate raises an optimistic-lock failure, which
 * {@code JpaEventRepository.save} re-maps to the domain {@link OptimisticLockException}.
 * Exactly one writer wins; the DB shows the seat/zone changed once.
 *
 * <p>Wiring mirrors {@link CheckoutTransactionJpaTest}: {@code ticketing.persistence=jpa}
 * over H2 with {@code ddl-auto=create-drop}, seeding/startup disabled, the default MOCK
 * web environment, and a {@link TransactionTemplate} to race two transactions. We drive
 * the inventory mutation directly on the aggregate (the same calls the OrderService
 * reservation/checkout path makes) so the test isolates the locking mechanism.
 */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(
        properties = {
                "ticketing.persistence=jpa",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "ticketing.seed.enabled=false",
                "ticketing.startup.initialize-platform=false"
        })
@DisplayName("Inventory JPA optimistic locking & no double-sell (V3-11 #269)")
class SeatReservationLockingJpaTest {

    @Autowired
    private IEventRepository eventRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    // ── Same-seat race: two orders lock the SAME assigned seat ────────────────────

    @Test
    @DisplayName("Given two transactions reserving the same seat, When both commit, Then exactly one succeeds and the seat is locked once")
    void GivenTwoTransactionsReservingSameSeat_WhenBothCommit_ThenExactlyOneSucceeds() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        eventRepository.save(publishedAssignedEvent(eventId, zoneId, seatId));

        // Both transactions read the event as independent detached snapshots; each sees
        // the seat AVAILABLE at the same child @Version.
        Event snapshotA = eventRepository.findById(eventId).orElseThrow();
        Event snapshotB = eventRepository.findById(eventId).orElseThrow();
        assertThat(snapshotA.findZone(zoneId).findSeat(seatId).getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(snapshotB.findZone(zoneId).findSeat(seatId).getStatus()).isEqualTo(SeatStatus.AVAILABLE);

        // Transaction A locks the seat first and commits → bumps the seat's @Version.
        transactionTemplate.executeWithoutResult(status -> {
            snapshotA.findZone(zoneId).lockSeat(seatId);
            eventRepository.save(snapshotA);
        });

        // Transaction B, built on the now-stale seat version, is rejected by the guard.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            snapshotB.findZone(zoneId).lockSeat(seatId);
            eventRepository.save(snapshotB);
        })).isInstanceOf(OptimisticLockException.class);

        // DB ends consistent: the seat is LOCKED exactly once — no double reservation.
        Seat persisted = eventRepository.findById(eventId).orElseThrow().findZone(zoneId).findSeat(seatId);
        assertThat(persisted.getStatus())
                .as("exactly one writer locked the seat despite two concurrent reservations")
                .isEqualTo(SeatStatus.LOCKED);
    }

    // ── Last-GA-ticket race: capacity 1, two concurrent GA reservations ───────────

    @Test
    @DisplayName("Given two transactions reserving the last GA ticket, When both commit, Then exactly one succeeds and one ticket is locked")
    void GivenTwoTransactionsReservingLastGaTicket_WhenBothCommit_ThenExactlyOneSucceeds() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        eventRepository.save(publishedGaEvent(eventId, zoneId, /* capacity */ 1));

        // Independent detached snapshots, each seeing availableCount=1 at the same zone version.
        Event snapshotA = eventRepository.findById(eventId).orElseThrow();
        Event snapshotB = eventRepository.findById(eventId).orElseThrow();
        assertThat(snapshotA.findZone(zoneId).getAvailableCount()).isEqualTo(1);
        assertThat(snapshotB.findZone(zoneId).getAvailableCount()).isEqualTo(1);

        // Transaction A locks the only ticket and commits → bumps the zone's @Version.
        transactionTemplate.executeWithoutResult(status -> {
            snapshotA.findZone(zoneId).lockGA(1);
            eventRepository.save(snapshotA);
        });

        // Transaction B, built on the now-stale zone version, is rejected by the guard.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            snapshotB.findZone(zoneId).lockGA(1);
            eventRepository.save(snapshotB);
        })).isInstanceOf(OptimisticLockException.class);

        // DB ends consistent: exactly one ticket locked, none over-sold.
        InventoryZone persisted = eventRepository.findById(eventId).orElseThrow().findZone(zoneId);
        assertThat(persisted.getLockedCount())
                .as("exactly one writer locked the last ticket despite two concurrent reservations")
                .isEqualTo(1);
        assertThat(persisted.getAvailableCount())
                .as("the single ticket is not double-counted")
                .isZero();
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private static Event publishedGaEvent(UUID eventId, UUID zoneId, int capacity) {
        Event event = newDraftEvent(eventId);
        event.addZone(InventoryZone.createGA(zoneId, "Floor", new BigDecimal("45.00"), capacity));
        event.publish();
        return event;
    }

    private static Event publishedAssignedEvent(UUID eventId, UUID zoneId, UUID seatId) {
        Event event = newDraftEvent(eventId);
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "Orchestra", new BigDecimal("99.00"));
        zone.addSeat(new Seat(seatId, "A", "1"));
        event.addZone(zone);
        event.publish();
        return event;
    }

    private static Event newDraftEvent(UUID eventId) {
        Instant start = Instant.now().plus(Duration.ofDays(30));
        return new Event(
                eventId, "Acme Productions", "Race Fest", "desc",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(Duration.ofHours(3)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(30)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy());
    }
}
