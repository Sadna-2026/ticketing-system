package com.ticketing.domain.order;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OrderBranchCoverageTest {

    @Test
    void GivenInvalidActiveOrderInputs_WhenConstructedOrUpdated_ThenValidationRejectsThem() {
        UUID id = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        ActiveOrder order = new ActiveOrder(id, sessionId, eventId, createdAt);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new ActiveOrder(null, sessionId, eventId, createdAt)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ActiveOrder(id, null, eventId, createdAt)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ActiveOrder(id, sessionId, null, createdAt)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ActiveOrder(id, sessionId, eventId, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> order.updateSessionId(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> order.updateMemberId(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> order.addItem(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> order.removeItem(UUID.randomUUID()))
        );
    }

    @Test
    void GivenActiveOrder_WhenItemsAndStateTransitionsRun_ThenBranchesAreCovered() {
        ActiveOrder order = order();
        UUID memberId = UUID.randomUUID();
        UUID newSessionId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        OrderItem ga = OrderItem.forGA(UUID.randomUUID(), zoneId, 2, BigDecimal.valueOf(25));
        OrderItem seat = OrderItem.forSeat(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(40));

        order.updateSessionId(newSessionId);
        order.updateMemberId(memberId);
        order.addItem(ga);
        order.addItem(seat);
        order.incrementVersion();

        assertAll(
                () -> assertEquals(newSessionId, order.getSessionId()),
                () -> assertEquals(memberId, order.getMemberId()),
                () -> assertEquals(1, order.getVersion()),
                () -> assertTrue(order.isActive()),
                () -> assertFalse(order.isExpired()),
                () -> assertEquals(3, order.getTotalTicketCount()),
                () -> assertEquals(BigDecimal.valueOf(90), order.getTotalPrice()),
                () -> assertTrue(order.findItemByZoneId(zoneId).isPresent()),
                () -> assertTrue(order.findItemByZoneId(UUID.randomUUID()).isEmpty()),
                () -> assertEquals(2, order.getItemsDto().size()),
                () -> assertFalse(order.isExpiredAt(order.getCreatedAt().plus(Duration.ofMinutes(1)), Duration.ofMinutes(10))),
                () -> assertTrue(order.isExpiredAt(order.getCreatedAt().plus(Duration.ofMinutes(11)), Duration.ofMinutes(10)))
        );

        ActiveOrder simulatedWithoutExtra = order.simulateWithAdditionalTickets(0);
        ActiveOrder simulatedWithExtra = order.simulateWithAdditionalTickets(3);
        assertEquals(order.getTotalTicketCount(), simulatedWithoutExtra.getTotalTicketCount());
        assertEquals(order.getTotalTicketCount() + 3, simulatedWithExtra.getTotalTicketCount());

        order.removeItem(seat.getId());
        assertEquals(1, order.getItems().size());

        order.startCheckout();
        assertEquals(OrderStatus.CHECKOUT_IN_PROGRESS, order.getStatus());
        assertThrows(IllegalStateException.class, () -> order.addItem(ga));
        order.revertToActive();
        assertEquals(OrderStatus.ACTIVE, order.getStatus());

        order.startCheckout();
        order.complete();
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertThrows(IllegalStateException.class, order::expire);
        assertThrows(IllegalStateException.class, order::cancel);
    }

    @Test
    void GivenOrderInWrongStates_WhenTransitioning_ThenInvalidStateBranchesThrow() {
        ActiveOrder empty = order();
        assertThrows(IllegalStateException.class, empty::startCheckout);
        assertThrows(IllegalStateException.class, empty::revertToActive);
        assertThrows(IllegalStateException.class, empty::complete);

        ActiveOrder cancelled = order();
        cancelled.cancel();
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertThrows(IllegalStateException.class, () -> cancelled.addItem(OrderItem.forGA(UUID.randomUUID(), UUID.randomUUID(), 1, BigDecimal.ONE)));

        ActiveOrder expired = order();
        expired.expire();
        assertTrue(expired.isExpired());
        assertThrows(IllegalStateException.class, () -> expired.addItem(OrderItem.forGA(UUID.randomUUID(), UUID.randomUUID(), 1, BigDecimal.ONE)));
    }

    @Test
    void GivenOrderItem_WhenConstructingUpdatingAndComparing_ThenBranchesAreCovered() {
        UUID id = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        OrderItem ga = OrderItem.forGA(id, zoneId, 2, BigDecimal.valueOf(12));
        OrderItem sameId = OrderItem.forGA(id, UUID.randomUUID(), 1, BigDecimal.ONE);
        OrderItem seat = OrderItem.forSeat(UUID.randomUUID(), zoneId, seatId, BigDecimal.valueOf(40));

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> OrderItem.forGA(UUID.randomUUID(), zoneId, 0, BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class, () -> OrderItem.forSeat(UUID.randomUUID(), zoneId, null, BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class, () -> OrderItem.forGA(null, zoneId, 1, BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class, () -> OrderItem.forGA(UUID.randomUUID(), null, 1, BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class, () -> OrderItem.forGA(UUID.randomUUID(), zoneId, 1, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> OrderItem.forGA(UUID.randomUUID(), zoneId, 1, BigDecimal.valueOf(-1))),
                () -> assertTrue(ga.isGA()),
                () -> assertFalse(ga.isAssignedSeat()),
                () -> assertTrue(seat.isAssignedSeat()),
                () -> assertFalse(seat.isGA()),
                () -> assertEquals(BigDecimal.valueOf(24), ga.getTotalPrice()),
                () -> assertNotNull(ga.getOrderItemDto()),
                () -> assertEquals(ga, ga),
                () -> assertEquals(ga, sameId),
                () -> assertNotEquals(ga, seat),
                () -> assertNotEquals(ga, null),
                () -> assertNotEquals(ga, "item"),
                () -> assertThrows(IllegalStateException.class, () -> seat.updateQuantity(2)),
                () -> assertThrows(IllegalArgumentException.class, () -> ga.updateQuantity(0))
        );

        ga.updateQuantity(3);
        assertEquals(3, ga.getQuantity());
    }

    @Test
    void GivenCompletedPurchaseAndBuyerSnapshot_WhenConstructing_ThenValidationAndEqualityBranchesAreCovered() {
        UUID purchaseId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Instant purchasedAt = Instant.parse("2026-01-01T00:00:00Z");
        CompletedPurchase purchase = new CompletedPurchase(
                purchaseId, eventId, "Show", "Acme", memberId, "tx", BigDecimal.TEN, purchasedAt);

        assertAll(
                () -> assertEquals(purchaseId, purchase.purchaseId()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CompletedPurchase(null, eventId, "Show", "Acme", memberId, "tx", BigDecimal.TEN, purchasedAt)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CompletedPurchase(purchaseId, null, "Show", "Acme", memberId, "tx", BigDecimal.TEN, purchasedAt)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CompletedPurchase(purchaseId, eventId, null, "Acme", memberId, "tx", BigDecimal.TEN, purchasedAt)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CompletedPurchase(purchaseId, eventId, " ", "Acme", memberId, "tx", BigDecimal.TEN, purchasedAt)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CompletedPurchase(purchaseId, eventId, "Show", null, memberId, "tx", BigDecimal.TEN, purchasedAt)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CompletedPurchase(purchaseId, eventId, "Show", " ", memberId, "tx", BigDecimal.TEN, purchasedAt)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CompletedPurchase(purchaseId, eventId, "Show", "Acme", memberId, null, BigDecimal.TEN, purchasedAt)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CompletedPurchase(purchaseId, eventId, "Show", "Acme", memberId, " ", BigDecimal.TEN, purchasedAt)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CompletedPurchase(purchaseId, eventId, "Show", "Acme", memberId, "tx", null, purchasedAt)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CompletedPurchase(purchaseId, eventId, "Show", "Acme", memberId, "tx", BigDecimal.valueOf(-1), purchasedAt)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CompletedPurchase(purchaseId, eventId, "Show", "Acme", memberId, "tx", BigDecimal.TEN, null))
        );

        BuyerContactSnapshot empty = BuyerContactSnapshot.empty();
        BuyerContactSnapshot first = new BuyerContactSnapshot("a@example.com", "alice", "050");
        BuyerContactSnapshot same = new BuyerContactSnapshot("a@example.com", "alice", "050");
        BuyerContactSnapshot different = new BuyerContactSnapshot("b@example.com", "alice", "050");

        assertAll(
                () -> assertTrue(empty.isEmpty()),
                () -> assertFalse(first.isEmpty()),
                () -> assertEquals(first, first),
                () -> assertEquals(first, same),
                () -> assertNotEquals(first, different),
                () -> assertNotEquals(first, null),
                () -> assertNotEquals(first, "snapshot"),
                () -> assertEquals(first.hashCode(), same.hashCode())
        );
    }

    @Test
    void GivenDomainSelectionRequest_WhenConstructed_ThenNullListsDuplicatesAndPickValidationBranchesAreCovered() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        SelectionRequest empty = new SelectionRequest(eventId, null, null);
        SelectionRequest selected = new SelectionRequest(eventId,
                List.of(new SelectionRequest.SeatPick(zoneId, seatId)),
                List.of(new SelectionRequest.GAPick(zoneId, 2)));

        assertAll(
                () -> assertTrue(empty.isEmpty()),
                () -> assertFalse(selected.isEmpty()),
                () -> assertThrows(IllegalArgumentException.class, () -> new SelectionRequest(null, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SelectionRequest(eventId,
                                List.of(new SelectionRequest.SeatPick(zoneId, seatId),
                                        new SelectionRequest.SeatPick(zoneId, seatId)),
                                List.of())),
                () -> assertThrows(IllegalArgumentException.class, () -> new SelectionRequest.SeatPick(null, seatId)),
                () -> assertThrows(IllegalArgumentException.class, () -> new SelectionRequest.SeatPick(zoneId, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new SelectionRequest.GAPick(null, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new SelectionRequest.GAPick(zoneId, 0))
        );
    }

    private static ActiveOrder order() {
        return new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"));
    }
}
