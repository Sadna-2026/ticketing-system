package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.event.InMemoryEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.InMemoryActiveOrderRepository;
import com.ticketing.domain.order.InMemoryCompletedOrderRepository;
import com.ticketing.domain.order.OrderStatus;

public class OrderServiceTest {

    private OrderService orderService;
    private InMemoryActiveOrderRepository activeOrderRepo;
    private InMemoryCompletedOrderRepository completedOrderRepo;
    private InMemoryEventRepository eventRepo;
    private InMemorySessionRepository sessionRepo;
    private InMemoryMemberRepository memberRepo;
    private InMemoryCompanyRepository companyRepo;
    private StubPaymentGateway paymentGateway;
    private StubTicketSupplyService ticketSupplyService;
    private InMemoryDomainEventPublisher eventPublisher;
    private TestClock clock;

    // Test fixtures
    private UUID memberId;
    private UUID sessionId;
    private UUID eventId;
    private UUID companyId;
    private UUID gaZoneId;
    private UUID assignedZoneId;
    private UUID seatId;
    private String memberToken;

    @BeforeEach
    void setUp() {
        activeOrderRepo = new InMemoryActiveOrderRepository();
        completedOrderRepo = new InMemoryCompletedOrderRepository();
        eventRepo = new InMemoryEventRepository();
        sessionRepo = new InMemorySessionRepository();
        memberRepo = new InMemoryMemberRepository();
        companyRepo = new InMemoryCompanyRepository();
        paymentGateway = new StubPaymentGateway();
        ticketSupplyService = new StubTicketSupplyService();
        eventPublisher = new InMemoryDomainEventPublisher();
        clock = new TestClock(Instant.parse("2026-06-01T10:00:00Z"));
        testTokenService = new TestTokenService();

        orderService = new OrderService(
                activeOrderRepo, completedOrderRepo, eventRepo, sessionRepo,
                memberRepo, companyRepo, paymentGateway, ticketSupplyService,
                eventPublisher, new PolicyResolver(), testTokenService, clock);

        // Set up test fixtures
        setupMemberAndSession();
        setupCompanyAndEvent();
    }


    @Test
    void GivenValidOrder_WhenAddGAAndSeatThenCheckout_ThenCompletesWithCorrectSnapshotsAndInventory() {
        // --- Create order ---
        UUID orderId = orderService.createOrder(, eventId);
        ActiveOrder order = activeOrderRepo.findById(orderId).orElseThrow();
        assertTrue(order.isActive());

        // --- Add GA tickets and verify inventory locked ---
        orderService.addGATicketsToOrder(null, orderId, gaZoneId, 3);
        Event eventAfterGA = eventRepo.findById(eventId).get();
        assertEquals(97, eventAfterGA.findZone(gaZoneId).getAvailableCount());
        assertEquals(3, eventAfterGA.findZone(gaZoneId).getLockedCount());

        // --- Add assigned seat and verify seat status is LOCKED ---
        orderService.addSeatToOrder(null, orderId, assignedZoneId, seatId);
        Event eventAfterSeat = eventRepo.findById(eventId).get();
        Seat lockedSeat = eventAfterSeat.findZone(assignedZoneId).findSeat(seatId);
        assertTrue(lockedSeat.isLocked());

        // --- Checkout ---
        eventPublisher.clear();
        UUID completedOrderId = orderService.checkout(null, orderId, null);

        // Verify CompletedOrder created with correct snapshot data
        CompletedOrder completed = completedOrderRepo.findById(completedOrderId).orElseThrow();
        assertEquals(memberId, completed.getMemberId());
        assertEquals("Summer Concert", completed.getEventSnapshot().getEventName());
        assertEquals("Test Productions", completed.getEventSnapshot().getCompanyName());
        assertNotNull(completed.getPaymentTransactionId());
        assertEquals(2, completed.getLineItems().size()); // GA item + seat item

        // Verify inventory moved from LOCKED to SOLD
        Event eventAfterCheckout = eventRepo.findById(eventId).get();
        InventoryZone gaZone = eventAfterCheckout.findZone(gaZoneId);
        assertEquals(97, gaZone.getAvailableCount());
        assertEquals(0, gaZone.getLockedCount());
        assertEquals(3, gaZone.getSoldCount());

        Seat soldSeat = eventAfterCheckout.findZone(assignedZoneId).findSeat(seatId);
        assertTrue(soldSeat.isSold());

        // Verify OrderCompletedEvent published
        assertTrue(eventPublisher.getPublishedEvents().stream()
                .anyMatch(e -> e instanceof OrderCompletedEvent));

        // Verify ActiveOrder status = COMPLETED
        ActiveOrder completedActive = activeOrderRepo.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, completedActive.getStatus());
    }

    @Test
    void GivenExistingActiveOrder_WhenCreateSecondOrderForSameSession_ThenRejectsAndFirstOrderIntact() {
        // Create first order and add tickets to make it meaningful
        UUID firstOrderId = orderService.createOrder(null, sessionId, eventId);
        orderService.addGATicketsToOrder(null, firstOrderId, gaZoneId, 2);

        // Attempt to create a second order for the same session
        assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(null, sessionId, eventId));

        // Verify the first order is still intact and active
        ActiveOrder firstOrder = activeOrderRepo.findById(firstOrderId).orElseThrow();
        assertTrue(firstOrder.isActive());
        assertEquals(2, firstOrder.getTotalTicketCount());
        assertEquals(eventId, firstOrder.getEventId());

        // Verify inventory is still locked from the first order
        Event event = eventRepo.findById(eventId).get();
        assertEquals(98, event.findZone(gaZoneId).getAvailableCount());
        assertEquals(2, event.findZone(gaZoneId).getLockedCount());
    }

    @Test
    void GivenTwoConcurrentSessions_WhenBothTryToLockSameSeat_ThenExactlyOneSucceeds() throws Exception {
        // Create a second session for a second buyer
        UUID memberId2 = UUID.randomUUID();
        ContactInfo contact2 = new ContactInfo("buyer2@test.com", "Jane", "Smith", "555-5678",
                LocalDate.of(1992, 5, 15));
        Member member2 = new Member(memberId2, contact2, "hashed-password-2", clock.now());
        memberRepo.save(member2);

        UUID sessionId2 = UUID.randomUUID();
        Session session2 = Session.createMemberSession(sessionId2, memberId2, clock.now());
        sessionRepo.save(session2);

        // Create two separate orders (one per session)
        UUID orderId1 = orderService.createOrder(null, sessionId, eventId);
        UUID orderId2 = orderService.createOrder(null, sessionId2, eventId);

        // Synchronize both threads to attempt seat lock simultaneously
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        AtomicReference<Exception> caughtException = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: try to lock the seat for order 1
        executor.submit(() -> {
            try {
                startLatch.await();
                orderService.addSeatToOrder(null, orderId1, assignedZoneId, seatId);
                successes.incrementAndGet();
            } catch (OptimisticLockException | IllegalStateException e) {
                failures.incrementAndGet();
                caughtException.set(e);
            } catch (Exception e) {
                failures.incrementAndGet();
                caughtException.set(e);
            }
        });

        // Thread 2: try to lock the seat for order 2
        executor.submit(() -> {
            try {
                startLatch.await();
                orderService.addSeatToOrder(null, orderId2, assignedZoneId, seatId);
                successes.incrementAndGet();
            } catch (OptimisticLockException | IllegalStateException e) {
                failures.incrementAndGet();
                caughtException.set(e);
            } catch (Exception e) {
                failures.incrementAndGet();
                caughtException.set(e);
            }
        });

        // Release both threads at the same time
        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        // Exactly one should succeed, exactly one should fail
        assertEquals(1, successes.get(), "Exactly one thread should succeed in locking the seat");
        assertEquals(1, failures.get(), "Exactly one thread should fail to lock the seat");

        // The seat should be locked exactly once
        Event event = eventRepo.findById(eventId).get();
        Seat seat = event.findZone(assignedZoneId).findSeat(seatId);
        assertTrue(seat.isLocked(), "Seat should be in LOCKED state");
        assertEquals(1, event.findZone(assignedZoneId).getLockedCount(),
                "Exactly one seat should be locked in the zone");
    }

    @Test
    void GivenOrderWithTickets_WhenLockTimerExpires_ThenOrderExpiredAndInventoryReleased() {
        // Create the expiration service using the same repos and clock
        OrderExpirationService expirationService = new OrderExpirationService(
                activeOrderRepo, eventRepo, eventPublisher, clock);

        // Create order and add GA tickets
        UUID orderId = orderService.createOrder(null, sessionId, eventId);
        orderService.addGATicketsToOrder(null, orderId, gaZoneId, 5);

        // Verify inventory is locked
        Event eventBefore = eventRepo.findById(eventId).get();
        assertEquals(95, eventBefore.findZone(gaZoneId).getAvailableCount());
        assertEquals(5, eventBefore.findZone(gaZoneId).getLockedCount());

        // Advance clock past the 15-minute lock timer duration
        clock.advance(Duration.ofMinutes(16));

        // Clear events to isolate the expiration event
        eventPublisher.clear();

        // Run expiration sweep
        expirationService.expireOrders();

        // Verify order status = EXPIRED
        ActiveOrder expiredOrder = activeOrderRepo.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.EXPIRED, expiredOrder.getStatus());

        // Verify inventory released (availableCount restored, lockedCount = 0)
        Event eventAfter = eventRepo.findById(eventId).get();
        assertEquals(100, eventAfter.findZone(gaZoneId).getAvailableCount());
        assertEquals(0, eventAfter.findZone(gaZoneId).getLockedCount());

        // Verify OrderExpiredEvent published
        assertTrue(eventPublisher.getPublishedEvents().stream()
                .anyMatch(e -> e instanceof OrderExpiredEvent));
    }
}

}
