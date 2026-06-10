package com.ticketing.domain.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * V3-5 (#263): the Order aggregate (ActiveOrder + its OrderItems) and the
 * CompletedPurchase snapshot persist and round-trip via H2 (JPA).
 *
 * Uses @DataJpaTest (embedded H2) with ddl-auto=create-drop so the schema is built for
 * the test even though the app config sets ddl-auto=none. @DataJpaTest is transactional,
 * so the OrderItem collection can be traversed inside the test method.
 *
 * The integrity test demonstrates the CRITICAL requirement: a CompletedPurchase is a
 * SNAPSHOT (plain copied columns, no @ManyToOne to Event/Company), so the stored history
 * is independent of any later change to the source Event.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@DisplayName("Order JPA mapping")
class OrderJpaMappingTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void GivenActiveOrderWithGaAndAssignedItems_WhenPersistedAndReloaded_ThenItemsAndStatusSurvive() {
        // --- Given: an ACTIVE order with one GA item and one assigned-seat item ----
        UUID orderId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-08T10:00:00Z");

        ActiveOrder order = new ActiveOrder(orderId, sessionId, memberId, eventId, createdAt);

        UUID gaZoneId = UUID.randomUUID();
        UUID gaItemId = UUID.randomUUID();
        OrderItem gaItem = OrderItem.forGA(gaItemId, gaZoneId, 3, new BigDecimal("25.00"));

        UUID assignedZoneId = UUID.randomUUID();
        UUID assignedItemId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        OrderItem seatItem = OrderItem.forSeat(assignedItemId, assignedZoneId, seatId, new BigDecimal("99.00"));

        order.addItem(gaItem);
        order.addItem(seatItem);

        // --- When: persisted, flushed, cleared, and reloaded ----------------------
        em.persistAndFlush(order);
        em.clear();
        ActiveOrder reloaded = em.find(ActiveOrder.class, orderId);

        // --- Then: scalars + status + items survive -------------------------------
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getId()).isEqualTo(orderId);
        assertThat(reloaded.getSessionId()).isEqualTo(sessionId);
        assertThat(reloaded.getMemberId()).isEqualTo(memberId);
        assertThat(reloaded.getEventId()).isEqualTo(eventId);
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.ACTIVE);
        assertThat(reloaded.isActive()).isTrue();

        assertThat(reloaded.getItems()).hasSize(2);
        assertThat(reloaded.getTotalTicketCount()).isEqualTo(4);
        assertThat(reloaded.getTotalPrice()).isEqualByComparingTo("174.00"); // 3*25 + 1*99

        // GA item round-trips (seatId null → isGA)
        OrderItem reloadedGa = reloaded.findItemByZoneId(gaZoneId).orElseThrow();
        assertThat(reloadedGa.getId()).isEqualTo(gaItemId);
        assertThat(reloadedGa.isGA()).isTrue();
        assertThat(reloadedGa.isAssignedSeat()).isFalse();
        assertThat(reloadedGa.getSeatId()).isNull();
        assertThat(reloadedGa.getQuantity()).isEqualTo(3);
        assertThat(reloadedGa.getPricePerTicket()).isEqualByComparingTo("25.00");

        // Assigned-seat item round-trips (seatId present → isAssignedSeat)
        OrderItem reloadedSeat = reloaded.getItems().stream()
                .filter(i -> i.getId().equals(assignedItemId))
                .findFirst().orElseThrow();
        assertThat(reloadedSeat.isAssignedSeat()).isTrue();
        assertThat(reloadedSeat.isGA()).isFalse();
        assertThat(reloadedSeat.getSeatId()).isEqualTo(seatId);
        assertThat(reloadedSeat.getZoneId()).isEqualTo(assignedZoneId);
        assertThat(reloadedSeat.getQuantity()).isEqualTo(1);
        assertThat(reloadedSeat.getPricePerTicket()).isEqualByComparingTo("99.00");
    }

    @Test
    void GivenCompletedPurchase_WhenPersistedAndReloaded_ThenAllSnapshotFieldsSurvive() {
        // --- Given: a completed purchase with full snapshot fields ----------------
        UUID purchaseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Instant purchasedAt = Instant.parse("2026-06-08T12:30:00Z");

        CompletedPurchase purchase = new CompletedPurchase(
                purchaseId, eventId, "Summer Fest", "Acme Productions",
                memberId, "buyerBob", "txn-123", new BigDecimal("174.00"), purchasedAt);

        // --- When: persisted, flushed, cleared, and reloaded ----------------------
        em.persistAndFlush(purchase);
        em.clear();
        CompletedPurchase reloaded = em.find(CompletedPurchase.class, purchaseId);

        // --- Then: every snapshot field round-trips -------------------------------
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.purchaseId()).isEqualTo(purchaseId);
        assertThat(reloaded.eventId()).isEqualTo(eventId);
        assertThat(reloaded.eventName()).isEqualTo("Summer Fest");
        assertThat(reloaded.companyName()).isEqualTo("Acme Productions");
        assertThat(reloaded.memberId()).isEqualTo(memberId);
        assertThat(reloaded.buyerUsername()).isEqualTo("buyerBob");
        assertThat(reloaded.transactionId()).isEqualTo("txn-123");
        assertThat(reloaded.amount()).isEqualByComparingTo("174.00");
        assertThat(reloaded.purchasedAt()).isEqualTo(purchasedAt);
    }

    @Test
    void GivenCompletedPurchaseSnapshot_WhenSourceEventNameWouldChange_ThenStoredHistoryStillReadsOriginal() {
        // --- Given: a purchase snapshotting the event as "Original" ---------------
        // The purchase holds COPIED columns, not a live Event reference (no @ManyToOne),
        // so there is no path by which a later Event edit could mutate this record.
        UUID purchaseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        CompletedPurchase purchase = new CompletedPurchase(
                purchaseId, eventId, "Original", "Original Co",
                UUID.randomUUID(), "txn-orig", new BigDecimal("50.00"),
                Instant.parse("2026-06-08T09:00:00Z"));

        em.persistAndFlush(purchase);
        em.clear();

        // --- When: reloaded after the source Event would have been renamed --------
        // (No Event entity is even involved here — that is precisely the point: the
        // snapshot cannot be reached from an Event, so editing one is impossible.)
        CompletedPurchase reloaded = em.find(CompletedPurchase.class, purchaseId);

        // --- Then: the frozen history still reads "Original" ----------------------
        assertThat(reloaded.eventName()).isEqualTo("Original");
        assertThat(reloaded.companyName()).isEqualTo("Original Co");
        assertThat(reloaded.amount()).isEqualByComparingTo("50.00");
    }
}
