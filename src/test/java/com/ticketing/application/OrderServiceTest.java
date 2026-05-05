package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventDiscountPolicy;
import com.ticketing.domain.event.EventPurchasePolicy;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.event.VenueMap;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.infrastructure.InMemoryActiveOrderRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.domain.order.InMemoryCompletedOrderRepository;
import com.ticketing.domain.order.OrderStatus;
import com.ticketing.application.SessionTokenService;
import java.util.Base64;
public class OrderServiceTest {

    private ActiveOrderService orderService;
    private InMemoryActiveOrderRepository activeOrderRepo;
    //private InMemoryCompletedOrderRepository completedOrderRepo;
    private InMemoryEventRepository eventRepo;
    private SessionTokenService sessionRepo;
    //private InMemoryMemberRepository memberRepo;
    //private InMemoryCompanyRepository companyRepo;
    //private StubPaymentGateway paymentGateway;
    //private StubTicketSupplyService ticketSupplyService;
    //private InMemoryDomainEventPublisher eventPublisher;
    private TestClock clock;
    
    // Test fixtures
    // private UUID memberId;
    // private UUID sessionId;
    private UUID eventId;
    private UUID companyId;
    private UUID gaZoneId;
    private UUID assignedZoneId;
    private UUID seatId;
    // private String memberToken;
    private String guestToken;
    private InMemorySessionTokenRepository sessionTokenRepository;

    @BeforeEach
    void setUp() {
        activeOrderRepo = new InMemoryActiveOrderRepository();
        //TODO
        // completedOrderRepo = new InMemoryCompletedOrderRepository();
        eventRepo = new InMemoryEventRepository();

        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
        );

        sessionTokenRepository = new InMemorySessionTokenRepository();
        sessionRepo = new SessionTokenService(secret, 120, sessionTokenRepository);

        //memberRepo = new InMemoryMemberRepository();
        //companyRepo = new InMemoryCompanyRepository();
        //paymentGateway = new StubPaymentGateway();
        //ticketSupplyService = new StubTicketSupplyService();
        //eventPublisher = new InMemoryDomainEventPublisher();
        clock = new TestClock(Instant.parse("2026-06-01T10:00:00Z"));
        //testTokenService = new TestTokenService();

        orderService = new ActiveOrderService(activeOrderRepo, sessionRepo, eventRepo, clock);

        // Set up test fixtures
        //setupMemberAndSession();
        //setupCompanyAndEvent();
        guestToken = sessionRepo.generateGuestToken();
        setUpEvent();
    }

    private void setUpEvent(){
        // add event
        eventId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        EventSchedule schedule = new EventSchedule(
                Instant.parse("2026-07-01T20:00:00Z"),
                Instant.parse("2026-07-01T23:00:00Z"),
                Instant.parse("2026-07-01T19:00:00Z"));
        LockTimerDuration lockDuration = new LockTimerDuration(Duration.ofMinutes(15));
        //EventPurchasePolicy purchasePolicy = EventPurchasePolicy.createDefault(UUID.randomUUID());
        //EventDiscountPolicy discountPolicy = new EventDiscountPolicy(UUID.randomUUID());
        Event event = new Event(eventId, companyId, "Summer Concert", "A great concert",
                EventCategory.CONCERT, schedule, lockDuration);
        
        // Add a GA zone
        gaZoneId = UUID.randomUUID();
        InventoryZone gaZone = InventoryZone.createGA(gaZoneId, "General Floor", new BigDecimal("50.00"), 100);
        event.addZone(gaZone);

        // Add an assigned seating zone with one seat
        assignedZoneId = UUID.randomUUID();
        InventoryZone assignedZone = InventoryZone.createAssigned(assignedZoneId, "VIP", new BigDecimal("150.00"));
        seatId = UUID.randomUUID();
        assignedZone.addSeat(new Seat(seatId, "A", "1"));
        event.addZone(assignedZone);

        event.publish();

        eventRepo.save(event);
    }


    @Test
    void GivenValidOrder_WhenAddGAAndSeatThenCheckout_ThenCompletesWithCorrectSnapshotsAndInventory() {
        UUID orderId = orderService.createOrder(guestToken, eventId);
        ActiveOrder order = activeOrderRepo.findById(orderId).orElseThrow();
        assertTrue(order.isActive());

        // --- Add GA tickets and verify inventory locked ---
        orderService.addGATicketsToOrder(guestToken, orderId, gaZoneId, 3);
        Event eventAfterGA = eventRepo.findById(eventId).get();
        assertEquals(97, eventAfterGA.findZone(gaZoneId).getAvailableCount());
        assertEquals(3, eventAfterGA.findZone(gaZoneId).getLockedCount());

        // --- Add assigned seat and verify seat status is LOCKED ---
        orderService.addSeatToOrder(guestToken, orderId, assignedZoneId, seatId);
        Event eventAfterSeat = eventRepo.findById(eventId).get();
        Seat lockedSeat = eventAfterSeat.findZone(assignedZoneId).findSeat(seatId);
        assertTrue(lockedSeat.isLocked());

        // TODO: continue to checkout
    }

    @Test
    void GivenExistingActiveOrder_WhenCreateSecondOrderForSameSession_ThenRejectsAndFirstOrderIntact() {
        // Create first order and add tickets to make it meaningful
        UUID firstOrderId = orderService.createOrder(guestToken, eventId);
        orderService.addGATicketsToOrder(guestToken, firstOrderId, gaZoneId, 2);

        // Attempt to create a second order for the same session
        assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(guestToken, eventId));

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
        String guestToken2 = sessionRepo.generateGuestToken();

        // Create two separate orders (one per session)
        UUID orderId1 = orderService.createOrder(guestToken, eventId);
        UUID orderId2 = orderService.createOrder(guestToken2, eventId);

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
                orderService.addSeatToOrder(guestToken, orderId1, assignedZoneId, seatId);
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
                orderService.addSeatToOrder(guestToken2, orderId2, assignedZoneId, seatId);
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
    void GivenOrderWithTickets_WhenLockTimerExpires_ThenOrderExpiredAndInventoryReleased_Automatically() {
        UUID quickEventId = UUID.randomUUID();
        EventSchedule schedule = new EventSchedule(
                Instant.parse("2026-07-01T20:00:00Z"),
                Instant.parse("2026-07-01T23:00:00Z"),
                Instant.parse("2026-07-01T19:00:00Z"));
                
        LockTimerDuration shortLockDuration = new LockTimerDuration(Duration.ofMillis(200));         
        Event quickEvent = new Event(quickEventId, companyId, "Quick Concert", "A fast concert",
                EventCategory.CONCERT, schedule, shortLockDuration);
                
        UUID quickZoneId = UUID.randomUUID();
        quickEvent.addZone(InventoryZone.createGA(quickZoneId, "General Floor", new BigDecimal("50.00"), 100));
        quickEvent.publish();
        eventRepo.save(quickEvent);
        OrderTimeDomainService expirationService = new OrderTimeDomainService(
                activeOrderRepo, eventRepo, clock);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(expirationService::expireOrders, 0, 50, TimeUnit.MILLISECONDS);
        try {
            UUID orderId = orderService.createOrder(guestToken, quickEventId);
            orderService.addGATicketsToOrder(guestToken, orderId, quickZoneId, 5);
            Event eventBefore = eventRepo.findById(quickEventId).get();
            assertEquals(95, eventBefore.findZone(quickZoneId).getAvailableCount());
            assertEquals(5, eventBefore.findZone(quickZoneId).getLockedCount());
            clock.advance(Duration.ofMillis(250));
            Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ActiveOrder expiredOrder = activeOrderRepo.findById(orderId).orElseThrow();
                    assertEquals(OrderStatus.EXPIRED, expiredOrder.getStatus());
                });
            Event eventAfter = eventRepo.findById(quickEventId).get();
            assertEquals(100, eventAfter.findZone(quickZoneId).getAvailableCount());
            assertEquals(0, eventAfter.findZone(quickZoneId).getLockedCount());
            
            // assertTrue(eventPublisher.getPublishedEvents().stream()
            //         .anyMatch(e -> e instanceof OrderExpiredEvent));
                    
        } finally {
            executor.shutdownNow(); 
        }
    }

    @Test
    void GivenActiveOrder_WhenGetActiveOrder_ThenReturnsDtoWithCorrectData() {
        UUID orderId = orderService.createOrder(guestToken, eventId);
        orderService.addGATicketsToOrder(guestToken, orderId, gaZoneId, 3);

        var dto = orderService.getActiveOrder(guestToken, orderId);

        assertEquals(orderId, dto.getId());
        assertEquals(sessionRepo.extractSessionId(guestToken), dto.getSessionId());
        assertEquals(eventId, dto.getEventId());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals(1, dto.getItems().size());
        assertEquals(new BigDecimal("150.00"), dto.getTotalPrice());
    }

    @Test
    void GivenGAZoneWithLimitedInventory_WhenAddBeyondAvailable_ThenRejectsAndInventoryUnchanged() {
        UUID orderId = orderService.createOrder(guestToken, eventId);

        // GA zone has 100 tickets available — try to lock 101
        assertThrows(IllegalStateException.class,
                () -> orderService.addGATicketsToOrder(guestToken, orderId, gaZoneId, 101));

        // Verify inventory unchanged
        Event event = eventRepo.findById(eventId).get();
        assertEquals(100, event.findZone(gaZoneId).getAvailableCount());
        assertEquals(0, event.findZone(gaZoneId).getLockedCount());
    }

}
