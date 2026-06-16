package com.ticketing.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.OrderItem;

public class NoOrphanSeatPolicyTest {

    private final NoOrphanSeatPolicy policy = new NoOrphanSeatPolicy();

    @Test
    void GivenNoEvent_WhenEvaluated_ThenAllowed() {
        PurchaseContext ctx = new PurchaseContext(null, null, null, null, Set.of());
        assertTrue(policy.isAllowed(ctx).allowed());
    }

    @Test
    void GivenGAZoneOnly_WhenEvaluated_ThenAllowed() {
        Event event = createEvent();
        event.addZone(InventoryZone.createGA(UUID.randomUUID(), "GA", new BigDecimal("10"), 100));
        event.publish();

        PurchaseContext ctx = new PurchaseContext(null, null, null, event, Set.of());
        assertTrue(policy.isAllowed(ctx).allowed());
    }

    @Test
    void GivenAdjacentSelection_WhenEvaluated_ThenAllowed() {
        // Row: [1][2][3][4][5] — select seats 1,2 → seats 3,4,5 remain together, no orphans
        Event event = createEvent();
        UUID zoneId = UUID.randomUUID();
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "VIP", new BigDecimal("50"));
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID(), s3 = UUID.randomUUID(),
             s4 = UUID.randomUUID(), s5 = UUID.randomUUID();
        zone.addSeat(new Seat(s1, "A", "1"));
        zone.addSeat(new Seat(s2, "A", "2"));
        zone.addSeat(new Seat(s3, "A", "3"));
        zone.addSeat(new Seat(s4, "A", "4"));
        zone.addSeat(new Seat(s5, "A", "5"));
        event.addZone(zone);
        event.publish();

        PurchaseContext ctx = new PurchaseContext(null, null, null, event, Set.of(s1, s2));
        assertTrue(policy.isAllowed(ctx).allowed());
    }

    @Test
    void GivenSelectionLeavesOrphan_WhenEvaluated_ThenRejected() {
        // Row: [1][2][3][4] — select seats 1,3 → seat 2 is orphaned
        Event event = createEvent();
        UUID zoneId = UUID.randomUUID();
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "VIP", new BigDecimal("50"));
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID(),
             s3 = UUID.randomUUID(), s4 = UUID.randomUUID();
        zone.addSeat(new Seat(s1, "A", "1"));
        zone.addSeat(new Seat(s2, "A", "2"));
        zone.addSeat(new Seat(s3, "A", "3"));
        zone.addSeat(new Seat(s4, "A", "4"));
        event.addZone(zone);
        event.publish();

        PurchaseContext ctx = new PurchaseContext(null, null, null, event, Set.of(s1, s3));
        PolicyResult result = policy.isAllowed(ctx);
        assertFalse(result.allowed());
        assertEquals("ORPHAN_SEAT", result.errorCode());
        assertTrue(result.reason().contains("A-2"));
    }

    @Test
    void GivenSelectionLeavesOrphanAtEdge_WhenEvaluated_ThenRejected() {
        // Row: [1][2][3] — select seat 2 → seat 1 is orphaned at edge
        Event event = createEvent();
        UUID zoneId = UUID.randomUUID();
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "VIP", new BigDecimal("50"));
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID(), s3 = UUID.randomUUID();
        zone.addSeat(new Seat(s1, "A", "1"));
        zone.addSeat(new Seat(s2, "A", "2"));
        zone.addSeat(new Seat(s3, "A", "3"));
        event.addZone(zone);
        event.publish();

        PurchaseContext ctx = new PurchaseContext(null, null, null, event, Set.of(s2));
        PolicyResult result = policy.isAllowed(ctx);
        assertFalse(result.allowed());
        assertEquals("ORPHAN_SEAT", result.errorCode());
    }

    @Test
    void GivenAlreadyLockedNeighbor_WhenSelectionLeavesOrphan_ThenRejected() {
        // Row: [X][2][3][4] — seat 1 already locked, select seat 3 → seat 2 orphaned
        Event event = createEvent();
        UUID zoneId = UUID.randomUUID();
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "VIP", new BigDecimal("50"));
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID(),
             s3 = UUID.randomUUID(), s4 = UUID.randomUUID();
        zone.addSeat(new Seat(s1, "A", "1"));
        zone.addSeat(new Seat(s2, "A", "2"));
        zone.addSeat(new Seat(s3, "A", "3"));
        zone.addSeat(new Seat(s4, "A", "4"));
        event.addZone(zone);
        event.publish();

        zone.lockSeat(s1);

        PurchaseContext ctx = new PurchaseContext(null, null, null, event, Set.of(s3));
        PolicyResult result = policy.isAllowed(ctx);
        assertFalse(result.allowed());
        assertEquals("ORPHAN_SEAT", result.errorCode());
        assertTrue(result.reason().contains("A-2"));
    }

    @Test
    void GivenMultipleRows_WhenOrphanInOneRow_ThenRejected() {
        Event event = createEvent();
        UUID zoneId = UUID.randomUUID();
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "VIP", new BigDecimal("50"));
        // Row A: [1][2][3] — all free, select nothing here
        UUID a1 = UUID.randomUUID(), a2 = UUID.randomUUID(), a3 = UUID.randomUUID();
        zone.addSeat(new Seat(a1, "A", "1"));
        zone.addSeat(new Seat(a2, "A", "2"));
        zone.addSeat(new Seat(a3, "A", "3"));
        // Row B: [1][2][3] — select 1 and 3, orphaning 2
        UUID b1 = UUID.randomUUID(), b2 = UUID.randomUUID(), b3 = UUID.randomUUID();
        zone.addSeat(new Seat(b1, "B", "1"));
        zone.addSeat(new Seat(b2, "B", "2"));
        zone.addSeat(new Seat(b3, "B", "3"));
        event.addZone(zone);
        event.publish();

        PurchaseContext ctx = new PurchaseContext(null, null, null, event, Set.of(b1, b3));
        PolicyResult result = policy.isAllowed(ctx);
        assertFalse(result.allowed());
        assertTrue(result.reason().contains("B-2"));
    }

    @Test
    void GivenAllSeatsSelected_WhenEvaluated_ThenAllowed() {
        Event event = createEvent();
        UUID zoneId = UUID.randomUUID();
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "VIP", new BigDecimal("50"));
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID(), s3 = UUID.randomUUID();
        zone.addSeat(new Seat(s1, "A", "1"));
        zone.addSeat(new Seat(s2, "A", "2"));
        zone.addSeat(new Seat(s3, "A", "3"));
        event.addZone(zone);
        event.publish();

        PurchaseContext ctx = new PurchaseContext(null, null, null, event, Set.of(s1, s2, s3));
        assertTrue(policy.isAllowed(ctx).allowed());
    }

    @Test
    void GivenSelectionLeavesNoOrphans_WhenEdgeSeatsRemainFree_ThenAllowed() {
        // Row: [1][2][3][4][5] — select seat 3 → seats 1,2 free together, seats 4,5 free together
        Event event = createEvent();
        UUID zoneId = UUID.randomUUID();
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "VIP", new BigDecimal("50"));
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID(), s3 = UUID.randomUUID(),
             s4 = UUID.randomUUID(), s5 = UUID.randomUUID();
        zone.addSeat(new Seat(s1, "A", "1"));
        zone.addSeat(new Seat(s2, "A", "2"));
        zone.addSeat(new Seat(s3, "A", "3"));
        zone.addSeat(new Seat(s4, "A", "4"));
        zone.addSeat(new Seat(s5, "A", "5"));
        event.addZone(zone);
        event.publish();

        PurchaseContext ctx = new PurchaseContext(null, null, null, event, Set.of(s3));
        assertTrue(policy.isAllowed(ctx).allowed());
    }

    @Test
    void GivenEmptyIncomingSeats_WhenExistingStateHasOrphan_ThenRejected() {
        // Checkout validation: seats already locked, orphan exists
        // Row: [X][ ][X] — seat 2 is orphaned
        Event event = createEvent();
        UUID zoneId = UUID.randomUUID();
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "VIP", new BigDecimal("50"));
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID(), s3 = UUID.randomUUID();
        zone.addSeat(new Seat(s1, "A", "1"));
        zone.addSeat(new Seat(s2, "A", "2"));
        zone.addSeat(new Seat(s3, "A", "3"));
        event.addZone(zone);
        event.publish();

        zone.lockSeat(s1);
        zone.lockSeat(s3);

        PurchaseContext ctx = new PurchaseContext(null, null, null, event, Set.of());
        PolicyResult result = policy.isAllowed(ctx);
        assertFalse(result.allowed());
        assertTrue(result.reason().contains("A-2"));
    }

    @Test
    void GivenSingleSeatRow_WhenSelected_ThenAllowed() {
        Event event = createEvent();
        UUID zoneId = UUID.randomUUID();
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "VIP", new BigDecimal("50"));
        UUID s1 = UUID.randomUUID();
        zone.addSeat(new Seat(s1, "A", "1"));
        event.addZone(zone);
        event.publish();

        PurchaseContext ctx = new PurchaseContext(null, null, null, event, Set.of(s1));
        assertTrue(policy.isAllowed(ctx).allowed());
    }

    @Test
    void GivenTwoSeatRow_WhenOneSelected_ThenOrphanDetected() {
        // Row: [1][2] — select seat 1 → seat 2 is orphaned at edge
        Event event = createEvent();
        UUID zoneId = UUID.randomUUID();
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "VIP", new BigDecimal("50"));
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID();
        zone.addSeat(new Seat(s1, "A", "1"));
        zone.addSeat(new Seat(s2, "A", "2"));
        event.addZone(zone);
        event.publish();

        PurchaseContext ctx = new PurchaseContext(null, null, null, event, Set.of(s1));
        PolicyResult result = policy.isAllowed(ctx);
        assertFalse(result.allowed());
        assertTrue(result.reason().contains("A-2"));
    }

    @Test
    void GivenEventWithoutPolicy_WhenNoOrphanNotAttached_ThenUnaffected() {
        Event event = createEvent();
        UUID zoneId = UUID.randomUUID();
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "VIP", new BigDecimal("50"));
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID(), s3 = UUID.randomUUID();
        zone.addSeat(new Seat(s1, "A", "1"));
        zone.addSeat(new Seat(s2, "A", "2"));
        zone.addSeat(new Seat(s3, "A", "3"));
        event.addZone(zone);
        event.publish();

        // Default AlwaysAllow policy — orphan selection should pass
        PurchaseContext ctx = new PurchaseContext(null, null, null, event, Set.of(s1, s3));
        assertTrue(event.getEventPurchasePolicy().isAllowed(ctx).allowed());
    }

    @Test
    void GivenComposedWithMaxQuantity_WhenBothViolated_ThenRejects() {
        Event event = createEvent();
        UUID zoneId = UUID.randomUUID();
        InventoryZone zone = InventoryZone.createAssigned(zoneId, "VIP", new BigDecimal("50"));
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID(), s3 = UUID.randomUUID();
        zone.addSeat(new Seat(s1, "A", "1"));
        zone.addSeat(new Seat(s2, "A", "2"));
        zone.addSeat(new Seat(s3, "A", "3"));
        event.addZone(zone);
        event.publish();

        ActiveOrder order = new ActiveOrder(UUID.randomUUID(), UUID.randomUUID(),
                event.getId(), Instant.now());
        order.addItem(OrderItem.forSeat(UUID.randomUUID(), zoneId, s1, new BigDecimal("50")));
        order.addItem(OrderItem.forSeat(UUID.randomUUID(), zoneId, s3, new BigDecimal("50")));

        IPurchasePolicy composed = new AndPolicy(List.of(new MaxQuantityPolicy(1), policy));
        PurchaseContext ctx = new PurchaseContext(order, null, null, event, Set.of(s1, s3));
        assertFalse(composed.isAllowed(ctx).allowed());
    }

    private Event createEvent() {
        return new Event(UUID.randomUUID(), "TestCo", "Show", "desc",
                EventCategory.CONCERT,
                new EventSchedule(
                        Instant.parse("2026-07-01T20:00:00Z"),
                        Instant.parse("2026-07-01T23:00:00Z"),
                        Instant.parse("2026-07-01T19:00:00Z")),
                new LockTimerDuration(Duration.ofMinutes(15)));
    }
}
