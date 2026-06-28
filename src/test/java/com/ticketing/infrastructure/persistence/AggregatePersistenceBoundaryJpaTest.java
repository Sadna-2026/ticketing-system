package com.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

/**
 * #493: aggregate persistence boundaries — load/save the WHOLE aggregate, never a
 * partial slice.
 *
 * <p>The DDD persistence-boundary rule that V0 / V3 modeling reinforces says an
 * aggregate is the unit of consistency: it loads as one graph and persists as one
 * transactional unit. A mid-save failure must leave the aggregate exactly as it was
 * before the call — no half-updated children, no orphans. This guarantee underpins
 * the {@code no double-sell} and {@code wipe-on-error} invariants because the
 * application services only have to reason about whole-aggregate commits, not
 * partial states.
 *
 * <p>This test exercises the two halves of that contract on the {@link Event}
 * aggregate (the most child-rich aggregate in the system):
 *
 * <ol>
 *   <li><b>Cascade on success</b>: mutating multiple children and saving the root once
 *       persists every child change atomically — confirms the
 *       {@code @OneToMany(cascade = ALL)} mapping on {@link Event#zones} and
 *       {@link InventoryZone#seats} is wired correctly.</li>
 *   <li><b>Cascade on rollback</b>: when the surrounding transaction rolls back after
 *       {@code eventRepository.save(event)} has been called, *none* of the child
 *       mutations survive. This is the all-or-nothing guarantee that legitimises the
 *       application services' "throw on payment failure → wipe on error" pattern.</li>
 * </ol>
 *
 * <p>The two repositories that own aggregate roots — {@code JpaEventRepository} for
 * Event and {@code JpaOrderRepository} for ActiveOrder — both go through
 * {@code entityManager.merge(...)} followed by an explicit {@code flush()}; the flush
 * is where the versioned UPDATE statements fire. The {@link TransactionTemplate}
 * provides the surrounding transaction; throwing inside the lambda triggers Spring's
 * standard rollback path.
 */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(
        properties = {
                "ticketing.persistence=jpa",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "ticketing.seed.enabled=false",
                "ticketing.startup.initialize-platform=false"
        })
@DisplayName("Aggregate persistence boundaries: whole-aggregate load/save (#493)")
class AggregatePersistenceBoundaryJpaTest {

    @Autowired
    private IEventRepository eventRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("Given multiple child mutations, When the aggregate root is saved once, Then every child mutation persists")
    void GivenMultipleChildMutations_WhenSaveAggregateRoot_ThenAllChildrenPersist() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID seatA = UUID.randomUUID();
        UUID seatB = UUID.randomUUID();
        UUID seatC = UUID.randomUUID();
        eventRepository.save(seededEvent(eventId, zoneId, List.of(seatA, seatB, seatC)));

        // Single load → multiple child mutations → single save. The cascade must persist
        // every dirty child without any per-child repository call.
        transactionTemplate.executeWithoutResult(status -> {
            Event snapshot = eventRepository.findById(eventId).orElseThrow();
            snapshot.findZone(zoneId).lockSeat(seatA);
            snapshot.findZone(zoneId).lockSeat(seatB);
            eventRepository.save(snapshot);
        });

        Event reloaded = eventRepository.findById(eventId).orElseThrow();
        assertThat(reloaded.findZone(zoneId).findSeat(seatA).getStatus()).isEqualTo(SeatStatus.LOCKED);
        assertThat(reloaded.findZone(zoneId).findSeat(seatB).getStatus()).isEqualTo(SeatStatus.LOCKED);
        assertThat(reloaded.findZone(zoneId).findSeat(seatC).getStatus())
                .as("untouched sibling must remain unaffected — the save does not leak across children")
                .isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("Given a mid-transaction failure after save, When the transaction rolls back, Then no child mutation persists")
    void GivenMidTransactionFailureAfterSave_WhenTransactionRollsBack_ThenAggregateIsIntact() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID seatA = UUID.randomUUID();
        UUID seatB = UUID.randomUUID();
        UUID seatC = UUID.randomUUID();
        eventRepository.save(seededEvent(eventId, zoneId, List.of(seatA, seatB, seatC)));

        // Mutate two children, save the root (which flushes), THEN throw — Spring rolls
        // back the whole transaction. If the cascade were not transactional, the seats
        // would already be LOCKED in the DB and the rollback couldn't undo them.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            Event snapshot = eventRepository.findById(eventId).orElseThrow();
            snapshot.findZone(zoneId).lockSeat(seatA);
            snapshot.findZone(zoneId).lockSeat(seatB);
            eventRepository.save(snapshot);
            throw new RuntimeException("simulated downstream failure after the save+flush");
        })).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("simulated downstream failure");

        Event reloaded = eventRepository.findById(eventId).orElseThrow();
        assertThat(reloaded.findZone(zoneId).findSeat(seatA).getStatus())
                .as("the rollback undid the locked-seat mutation atomically")
                .isEqualTo(SeatStatus.AVAILABLE);
        assertThat(reloaded.findZone(zoneId).findSeat(seatB).getStatus())
                .as("rollback covers every child the cascade touched, not just the last one")
                .isEqualTo(SeatStatus.AVAILABLE);
        assertThat(reloaded.findZone(zoneId).findSeat(seatC).getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(reloaded.findZone(zoneId).getLockedCount())
                .as("zone counts also revert — no ghost reservation count left behind")
                .isZero();
    }

    @Test
    @DisplayName("Given a freshly-loaded aggregate, When inspected, Then every child is present in the same load (no lazy-init failures across the boundary)")
    void GivenFreshlyLoadedAggregate_WhenInspectingChildren_ThenAllPresentInSameLoad() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID seatA = UUID.randomUUID();
        UUID seatB = UUID.randomUUID();
        eventRepository.save(seededEvent(eventId, zoneId, List.of(seatA, seatB)));

        // The JPA repositories return detached copies; the children must come along with
        // the root, not throw LazyInitializationException when accessed after the
        // transaction closes. This guards against accidental fetch=LAZY on a child the
        // aggregate boundary says we always need.
        Event reloaded = eventRepository.findById(eventId).orElseThrow();
        assertThat(reloaded.findZone(zoneId)).isNotNull();
        assertThat(reloaded.findZone(zoneId).findSeat(seatA)).isNotNull();
        assertThat(reloaded.findZone(zoneId).findSeat(seatB)).isNotNull();
        assertThat(reloaded.findZone(zoneId).getSeats()).hasSize(2);
    }

    // ── fixture ───────────────────────────────────────────────────────────────────

    private static Event seededEvent(UUID eventId, UUID zoneId, List<UUID> seatIds) {
        Event event = newDraftEvent(eventId);
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "Orchestra", new BigDecimal("75.00"));
        int rowIdx = 0;
        for (UUID seatId : seatIds) {
            zone.addSeat(new Seat(seatId, "A", String.valueOf(++rowIdx)));
        }
        event.addZone(zone);
        event.publish();
        return event;
    }

    private static Event newDraftEvent(UUID eventId) {
        Instant start = Instant.now().plus(Duration.ofDays(30));
        return new Event(
                eventId, "Acme Productions", "Boundary Fest", "desc",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(Duration.ofHours(3)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(30)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy());
    }
}
