package com.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.lottery.ILotteryRepository;
import com.ticketing.domain.lottery.LotteryEntry;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.order.OrderItem;

/**
 * V3-8 (#266): exercises the JPA-backed event, order, and lottery repositories
 * against embedded H2.
 *
 * <p>Uses {@code @DataJpaTest} (slices in the Spring Data JPA repos + an H2
 * EntityManager) with {@code ticketing.persistence=jpa} so the adapter beans'
 * {@link ConditionalOnProperty} matches, and {@code ddl-auto=create-drop} so the
 * schema is built for the test (the app config uses {@code ddl-auto=none}). The
 * three adapters are explicitly {@code @Import}ed because they live outside the
 * default {@code @DataJpaTest} scan. {@code @DataJpaTest} is transactional, so the
 * LAZY seat/item collections can be traversed inside the test methods.
 */
@DataJpaTest(properties = {
        "ticketing.persistence=jpa",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ JpaEventRepository.class, JpaOrderRepository.class, JpaLotteryRepository.class })
@DisplayName("JPA event, order & lottery repositories on H2")
class JpaEventOrderLotteryRepositoryTest {

    @Autowired
    private IEventRepository eventRepository;

    @Autowired
    private IOrderRepository orderRepository;

    @Autowired
    private ILotteryRepository lotteryRepository;

    // ── Event ─────────────────────────────────────────────────────────────────

    @Test
    void GivenEventWithZoneAndSeat_WhenSavedAndFetched_ThenAggregateRoundTrips() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Event event = newEvent(eventId, "Acme Productions", "Summer Fest", zoneId, seatId);

        eventRepository.save(event);
        var found = eventRepository.findById(eventId);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(eventId);
        assertThat(found.get().getName()).isEqualTo("Summer Fest");
        assertThat(found.get().getCompanyName()).isEqualTo("Acme Productions");
        assertThat(found.get().getZones()).hasSize(1);
        InventoryZone reloadedZone = found.get().findZone(zoneId);
        assertThat(reloadedZone.getSeats()).hasSize(1);
        assertThat(reloadedZone.findSeat(seatId).getRow()).isEqualTo("A");

        assertThat(eventRepository.findByCompanyName("Acme Productions")).hasSize(1);
        assertThat(eventRepository.findAll()).hasSize(1);
    }

    @Test
    void GivenTwoEventSnapshots_WhenSecondSaveIsStale_ThenDomainOptimisticLockException() {
        UUID eventId = UUID.randomUUID();
        eventRepository.save(newEvent(eventId, "Acme Productions", "Edit Me",
                UUID.randomUUID(), UUID.randomUUID()));

        Event first = eventRepository.findById(eventId).orElseThrow();
        Event second = eventRepository.findById(eventId).orElseThrow();

        first.setDescription("updated by first");
        eventRepository.save(first);

        second.setDescription("updated by second");
        assertThatExceptionOfType(OptimisticLockException.class)
                .isThrownBy(() -> eventRepository.save(second));
    }

    // ── Order (ActiveOrder) ─────────────────────────────────────────────────────

    @Test
    void GivenActiveOrderWithItems_WhenSavedAndFetched_ThenRoundTripsAndQueriesMatch() {
        UUID orderId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ActiveOrder order = new ActiveOrder(orderId, sessionId, memberId, eventId,
                Instant.parse("2026-06-08T10:00:00Z"));
        order.addItem(OrderItem.forGA(UUID.randomUUID(), UUID.randomUUID(), 3, new BigDecimal("25.00")));

        orderRepository.save(order);

        assertThat(orderRepository.findById(orderId)).isPresent();
        assertThat(orderRepository.findById(orderId).orElseThrow().getItems()).hasSize(1);
        assertThat(orderRepository.findActiveBySessionId(sessionId)).isPresent();
        assertThat(orderRepository.findActiveByMemberId(memberId)).isPresent();
        assertThat(orderRepository.findActiveByEventId(eventId)).hasSize(1);
        assertThat(orderRepository.findAllActive()).hasSize(1);
    }

    @Test
    void GivenTwoActiveOrderSnapshots_WhenSecondSaveIsStale_ThenDomainOptimisticLockException() {
        UUID orderId = UUID.randomUUID();
        ActiveOrder order = new ActiveOrder(orderId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), Instant.parse("2026-06-08T10:00:00Z"));
        order.addItem(OrderItem.forGA(UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("10.00")));
        orderRepository.save(order);

        ActiveOrder first = orderRepository.findById(orderId).orElseThrow();
        ActiveOrder second = orderRepository.findById(orderId).orElseThrow();

        first.addItem(OrderItem.forGA(UUID.randomUUID(), UUID.randomUUID(), 2, new BigDecimal("20.00")));
        orderRepository.save(first);

        second.addItem(OrderItem.forGA(UUID.randomUUID(), UUID.randomUUID(), 5, new BigDecimal("50.00")));
        assertThatExceptionOfType(OptimisticLockException.class)
                .isThrownBy(() -> orderRepository.save(second));
    }

    // ── Order (CompletedPurchase) ───────────────────────────────────────────────

    @Test
    void GivenCompletedPurchase_WhenSavedAndFetched_ThenAllQueriesMatch() {
        UUID purchaseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        CompletedPurchase purchase = new CompletedPurchase(
                purchaseId, eventId, "Summer Fest", "Acme Productions",
                memberId, "txn-123", new BigDecimal("174.00"),
                Instant.parse("2026-06-08T12:30:00Z"));

        orderRepository.save(purchase);

        assertThat(orderRepository.findCompletedById(purchaseId)).isPresent();
        assertThat(orderRepository.findCompletedById(purchaseId).orElseThrow().eventName())
                .isEqualTo("Summer Fest");
        assertThat(orderRepository.findCompletedByCompanyName("Acme Productions")).hasSize(1);
        assertThat(orderRepository.findCompletedByEventId(eventId)).hasSize(1);
        assertThat(orderRepository.findCompletedByMemberId(memberId)).hasSize(1);
        assertThat(orderRepository.findAllCompleted()).hasSize(1);
    }

    // ── Lottery ─────────────────────────────────────────────────────────────────

    @Test
    void GivenLotteryEntry_WhenSavedAndFetched_ThenRoundTripsAndQueriesMatch() {
        UUID entryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        LotteryEntry entry = new LotteryEntry(entryId, eventId, memberId, zoneId, 2,
                Instant.parse("2026-08-05T00:00:00Z"));

        lotteryRepository.save(entry);

        var found = lotteryRepository.findById(entryId);
        assertThat(found).isPresent();
        assertThat(found.get().eventId()).isEqualTo(eventId);
        assertThat(found.get().memberId()).isEqualTo(memberId);
        assertThat(found.get().zoneId()).isEqualTo(zoneId);
        assertThat(found.get().quantity()).isEqualTo(2);
        // First store bumps the version 0 → 1, mirroring the in-memory repo.
        assertThat(found.get().version()).isEqualTo(1);

        assertThat(lotteryRepository.findByEventId(eventId)).hasSize(1);
        assertThat(lotteryRepository.findByMemberId(memberId)).hasSize(1);
        assertThat(lotteryRepository.findByEventAndMember(eventId, memberId)).isPresent();
        assertThat(lotteryRepository.findAll()).hasSize(1);

        lotteryRepository.delete(entryId);
        assertThat(lotteryRepository.findById(entryId)).isEmpty();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private static Event newEvent(UUID eventId, String companyName, String name,
                                  UUID zoneId, UUID seatId) {
        EventSchedule schedule = new EventSchedule(
                Instant.parse("2026-09-01T20:00:00Z"),
                Instant.parse("2026-09-01T23:00:00Z"),
                Instant.parse("2026-09-01T19:00:00Z"));
        Event event = new Event(
                eventId, companyName, name, "desc",
                EventCategory.CONCERT, schedule, new LockTimerDuration(Duration.ofMinutes(10)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy());
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "Front Row", new BigDecimal("99.00"));
        zone.addSeat(new Seat(seatId, "A", "1"));
        event.addZone(zone);
        return event;
    }
}
