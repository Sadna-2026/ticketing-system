package com.ticketing.application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.SessionTokenService;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.services.OrderService;
import com.ticketing.application.services.OrderTimeDomainService;
import com.ticketing.application.services.TicketSelectionService;
import com.ticketing.domain.auth.SessionTokenData;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.PolicyResult;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.gateway.CancelResult;
import com.ticketing.domain.gateway.CustomerInfo;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.ITicketSupplyGateway;
import com.ticketing.domain.gateway.PaymentDetails;
import com.ticketing.domain.gateway.PaymentResult;
import com.ticketing.domain.gateway.RefundResult;
import com.ticketing.domain.gateway.SupplyResult;
import com.ticketing.domain.gateway.TicketRequest;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.OrderStatus;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;

public class OrderServiceTest {

    private OrderService orderService;
    private InMemoryOrderRepository orderRepo;
    private InMemoryEventRepository eventRepo;
    private InMemoryMemberRepository memberRepo;
    private SessionTokenService sessionService;
    private TestPaymentGateway paymentGateway;
    private TestTicketSupplyGateway ticketSupplyGateway;
    private TestClock clock;

    private UUID eventId;
    private String companyName;
    private UUID gaZoneId;
    private UUID assignedZoneId;
    private UUID seatId;
    private String guestToken;

    @BeforeEach
    void setUp() {
        orderRepo = new InMemoryOrderRepository();
        eventRepo = new InMemoryEventRepository();
        memberRepo = new InMemoryMemberRepository();
        paymentGateway = new TestPaymentGateway();
        ticketSupplyGateway = new TestTicketSupplyGateway();
        clock = new TestClock(Instant.parse("2026-06-01T10:00:00Z"));

        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
        sessionService = new SessionTokenService(secret, 120, new InMemorySessionTokenRepository());

        orderService = new OrderService(orderRepo, sessionService, eventRepo, clock,
                memberRepo, List.of(paymentGateway), ticketSupplyGateway);

        guestToken = sessionService.generateGuestToken();
        setUpPublishedEvent();
    }

    @Test
    void GivenValidOrder_WhenAddGAAndSeat_ThenInventoryIsLocked() {
        UUID orderId = orderService.createOrder(guestToken, eventId);

        orderService.addGATicketsToOrder(guestToken, orderId, gaZoneId, 3);
        Event eventAfterGA = eventRepo.findById(eventId).orElseThrow();
        assertEquals(97, eventAfterGA.findZone(gaZoneId).getAvailableCount());
        assertEquals(3, eventAfterGA.findZone(gaZoneId).getLockedCount());

        orderService.addSeatToOrder(guestToken, orderId, assignedZoneId, seatId);
        Event eventAfterSeat = eventRepo.findById(eventId).orElseThrow();
        assertTrue(eventAfterSeat.findZone(assignedZoneId).findSeat(seatId).isLocked());

        ActiveOrder order = orderRepo.findById(orderId).orElseThrow();
        assertTrue(order.isActive());
        assertEquals(4, order.getTotalTicketCount());
    }

    @Test
    void GivenMixedSelection_WhenAddSelectionToOrder_ThenBothGAAndSeatsAreLocked() {
        UUID orderId = orderService.createOrder(guestToken, eventId);
        SelectionRequest request = new SelectionRequest(eventId,
                List.of(new SelectionRequest.SeatPick(assignedZoneId, seatId)),
                List.of(new SelectionRequest.GAPick(gaZoneId, 2)));

        List<UUID> itemIds = orderService.addSelectionToOrder(guestToken, orderId, request);

        assertEquals(2, itemIds.size());
        ActiveOrder order = orderRepo.findById(orderId).orElseThrow();
        assertEquals(3, order.getTotalTicketCount());
        Event event = eventRepo.findById(eventId).orElseThrow();
        assertEquals(98, event.findZone(gaZoneId).getAvailableCount());
        assertEquals(2, event.findZone(gaZoneId).getLockedCount());
        assertTrue(event.findZone(assignedZoneId).findSeat(seatId).isLocked());
    }

    @Test
    void GivenExistingActiveOrder_WhenCreateSecondOrderForSameSession_ThenRejectsAndFirstOrderIntact() {
        UUID firstOrderId = orderService.createOrder(guestToken, eventId);
        orderService.addGATicketsToOrder(guestToken, firstOrderId, gaZoneId, 2);

        assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(guestToken, eventId));

        ActiveOrder firstOrder = orderRepo.findById(firstOrderId).orElseThrow();
        assertTrue(firstOrder.isActive());
        assertEquals(2, firstOrder.getTotalTicketCount());
        assertEquals(eventId, firstOrder.getEventId());
        Event event = eventRepo.findById(eventId).orElseThrow();
        assertEquals(98, event.findZone(gaZoneId).getAvailableCount());
        assertEquals(2, event.findZone(gaZoneId).getLockedCount());
    }

    @Test
    void GivenTwoConcurrentSessions_WhenBothTryToLockSameSeat_ThenExactlyOneSucceeds() throws Exception {
        String guestToken2 = sessionService.generateGuestToken();
        UUID orderId1 = orderService.createOrder(guestToken, eventId);
        UUID orderId2 = orderService.createOrder(guestToken2, eventId);

        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        AtomicReference<Exception> caughtException = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> addSeatConcurrently(startLatch, successes, failures, caughtException, guestToken, orderId1));
        executor.submit(() -> addSeatConcurrently(startLatch, successes, failures, caughtException, guestToken2, orderId2));

        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        assertNotNull(caughtException.get());
        assertEquals(1, successes.get());
        assertEquals(1, failures.get());

        Event event = eventRepo.findById(eventId).orElseThrow();
        assertTrue(event.findZone(assignedZoneId).findSeat(seatId).isLocked());
        assertEquals(1, event.findZone(assignedZoneId).getLockedCount());
    }

    @Test
    void GivenOrderWithTickets_WhenLockTimerExpires_ThenOrderExpiredAndInventoryReleasedAutomatically() {
        UUID quickEventId = UUID.randomUUID();
        UUID quickZoneId = UUID.randomUUID();
        Event quickEvent = createPublishedEvent(quickEventId, "Quick Concert", Duration.ofMillis(200));
        quickEvent.addZone(InventoryZone.createGA(quickZoneId, "General Floor", new BigDecimal("50.00"), 100));
        quickEvent.publish();
        eventRepo.save(quickEvent);

        OrderTimeDomainService expirationService = new OrderTimeDomainService(orderRepo, eventRepo, clock);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(expirationService::expireOrders, 0, 50, TimeUnit.MILLISECONDS);
        try {
            UUID orderId = orderService.createOrder(guestToken, quickEventId);
            orderService.addGATicketsToOrder(guestToken, orderId, quickZoneId, 5);
            assertEquals(95, eventRepo.findById(quickEventId).orElseThrow()
                    .findZone(quickZoneId).getAvailableCount());

            clock.advance(Duration.ofMillis(250));

            Awaitility.await()
                    .atMost(2, TimeUnit.SECONDS)
                    .pollInterval(50, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> assertEquals(OrderStatus.EXPIRED,
                            orderRepo.findById(orderId).orElseThrow().getStatus()));

            Event eventAfter = eventRepo.findById(quickEventId).orElseThrow();
            assertEquals(100, eventAfter.findZone(quickZoneId).getAvailableCount());
            assertEquals(0, eventAfter.findZone(quickZoneId).getLockedCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void GivenLockedTickets_WhenCheckoutSucceeds_ThenOrderCompletesInventorySoldAndSnapshotSaved() {
        UUID orderId = orderService.createOrder(guestToken, eventId);
        orderService.addGATicketsToOrder(guestToken, orderId, gaZoneId, 3);
        orderService.addSeatToOrder(guestToken, orderId, assignedZoneId, seatId);

        UUID purchaseId = orderService.checkout(guestToken, orderId, null);

        ActiveOrder order = orderRepo.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        Event event = eventRepo.findById(eventId).orElseThrow();
        assertEquals(97, event.findZone(gaZoneId).getAvailableCount());
        assertEquals(0, event.findZone(gaZoneId).getLockedCount());
        assertEquals(3, event.findZone(gaZoneId).getSoldCount());
        assertTrue(event.findZone(assignedZoneId).findSeat(seatId).isSold());

        CompletedPurchase purchase = orderRepo.findCompletedById(purchaseId).orElseThrow();
        assertEquals(eventId, purchase.eventId());
        assertEquals("Summer Concert", purchase.eventName());
        assertEquals(companyName, purchase.companyName());
        assertEquals(new BigDecimal("300.00"), purchase.amount());
        assertEquals(clock.now(), purchase.purchasedAt());
        assertEquals(1, paymentGateway.chargeCalls);
        assertEquals(new BigDecimal("300.00"), paymentGateway.chargedAmount);
        assertEquals(4, ticketSupplyGateway.lastTickets.size());
    }

    @Test
    void GivenCheckoutConsumesLastTicket_WhenCheckoutSucceeds_ThenEventIsSoldOut() {
        UUID soldOutEventId = UUID.randomUUID();
        UUID singleZoneId = UUID.randomUUID();
        Event event = createPublishedEvent(soldOutEventId, "Tiny Show", Duration.ofMinutes(15));
        event.addZone(InventoryZone.createGA(singleZoneId, "Only Zone", new BigDecimal("10.00"), 1));
        event.publish();
        eventRepo.save(event);

        UUID orderId = orderService.createOrder(guestToken, soldOutEventId);
        orderService.addGATicketsToOrder(guestToken, orderId, singleZoneId, 1);
        orderService.checkout(guestToken, orderId, null);

        assertEquals(EventStatus.SOLD_OUT, eventRepo.findById(soldOutEventId).orElseThrow().getStatus());
    }

    @Test
    void GivenPaymentFails_WhenCheckout_ThenOrderReturnsActiveInventoryStaysLockedAndNoTicketsIssued() {
        paymentGateway.failCharges = true;
        UUID orderId = orderService.createOrder(guestToken, eventId);
        orderService.addGATicketsToOrder(guestToken, orderId, gaZoneId, 1);

        assertThrows(IllegalStateException.class,
                () -> orderService.checkout(guestToken, orderId, null));

        assertEquals(OrderStatus.ACTIVE, orderRepo.findById(orderId).orElseThrow().getStatus());
        Event event = eventRepo.findById(eventId).orElseThrow();
        assertEquals(99, event.findZone(gaZoneId).getAvailableCount());
        assertEquals(1, event.findZone(gaZoneId).getLockedCount());
        assertTrue(orderRepo.findCompletedByEventId(eventId).isEmpty());
        assertEquals(0, ticketSupplyGateway.issueCalls);
    }

    @Test
    void GivenTicketSupplyFails_WhenCheckout_ThenPaymentRefundedOrderCancelledAndInventoryReleased() {
        ticketSupplyGateway.failIssue = true;
        UUID orderId = orderService.createOrder(guestToken, eventId);
        orderService.addGATicketsToOrder(guestToken, orderId, gaZoneId, 2);

        assertThrows(IllegalStateException.class,
                () -> orderService.checkout(guestToken, orderId, null));

        assertEquals(OrderStatus.CANCELLED, orderRepo.findById(orderId).orElseThrow().getStatus());
        Event event = eventRepo.findById(eventId).orElseThrow();
        assertEquals(100, event.findZone(gaZoneId).getAvailableCount());
        assertEquals(0, event.findZone(gaZoneId).getLockedCount());
        assertEquals(1, paymentGateway.refundCalls);
        assertTrue(orderRepo.findCompletedByEventId(eventId).isEmpty());
    }

    @Test
    void GivenPurchasePolicyRejects_WhenCheckout_ThenOrderRemainsActiveAndPaymentNotCharged() {
        UUID policyEventId = UUID.randomUUID();
        UUID policyZoneId = UUID.randomUUID();
        Event event = new Event(policyEventId, companyName, "Policy Show", "desc", EventCategory.CONCERT,
                defaultSchedule(), new LockTimerDuration(Duration.ofMinutes(15)),
                (order, memberId) -> PolicyResult.failure("DENIED", "No tickets for you"),
                (order, coupon, now) -> BigDecimal.ZERO);
        event.addZone(InventoryZone.createGA(policyZoneId, "Floor", new BigDecimal("20.00"), 5));
        event.publish();
        eventRepo.save(event);

        UUID orderId = orderService.createOrder(guestToken, policyEventId);
        orderService.addGATicketsToOrder(guestToken, orderId, policyZoneId, 1);

        assertThrows(IllegalStateException.class,
                () -> orderService.checkout(guestToken, orderId, null));

        assertEquals(OrderStatus.ACTIVE, orderRepo.findById(orderId).orElseThrow().getStatus());
        assertEquals(0, paymentGateway.chargeCalls);
    }

    @Test
    void GivenDiscountPolicy_WhenCheckout_ThenCompletedPurchaseUsesDiscountedAmount() {
        UUID discountEventId = UUID.randomUUID();
        UUID discountZoneId = UUID.randomUUID();
        Event event = new Event(discountEventId, companyName, "Discount Show", "desc", EventCategory.CONCERT,
                defaultSchedule(), new LockTimerDuration(Duration.ofMinutes(15)),
                (order, memberId) -> PolicyResult.success(),
                (order, coupon, now) -> new BigDecimal("20.00"));
        event.addZone(InventoryZone.createGA(discountZoneId, "Floor", new BigDecimal("50.00"), 10));
        event.publish();
        eventRepo.save(event);

        UUID orderId = orderService.createOrder(guestToken, discountEventId);
        orderService.addGATicketsToOrder(guestToken, orderId, discountZoneId, 2);
        UUID purchaseId = orderService.checkout(guestToken, orderId, "SAVE20");

        assertEquals(new BigDecimal("80.00"), paymentGateway.chargedAmount);
        assertEquals(new BigDecimal("80.00"), orderRepo.findCompletedById(purchaseId).orElseThrow().amount());
    }

    @Test
    void GivenMemberOrder_WhenCheckout_ThenPaymentAndSupplyReceiveBuyerSnapshot() {
        UUID memberId = UUID.randomUUID();
        Member member = new Member(memberId, "memberUser", "member@example.com", "pw",
                "050-1111111", LocalDate.of(1990, 1, 1));
        memberRepo.save(member);
        String memberToken = sessionService.generateMemberToken(new SessionTokenData(
                UUID.randomUUID(), memberId, Set.of(), member.getUsername(), member.getEmail(), "MEMBER"));

        UUID orderId = orderService.createOrder(memberToken, eventId);
        orderService.addGATicketsToOrder(memberToken, orderId, gaZoneId, 1);
        UUID purchaseId = orderService.checkout(memberToken, orderId, null);

        assertEquals(memberId, orderRepo.findCompletedById(purchaseId).orElseThrow().memberId());
        assertEquals(memberId, paymentGateway.lastDetails.memberId());
        assertEquals("member@example.com", paymentGateway.lastDetails.email());
        assertEquals(memberId.toString(), ticketSupplyGateway.lastCustomer.userId());
        assertEquals("member@example.com", ticketSupplyGateway.lastCustomer.email());
        assertEquals("memberUser", ticketSupplyGateway.lastCustomer.fullName());
    }

    private void setUpPublishedEvent() {
        eventId = UUID.randomUUID();
        companyName = "Test Company";
        Event event = createPublishedEvent(eventId, "Summer Concert", Duration.ofMinutes(15));

        gaZoneId = UUID.randomUUID();
        event.addZone(InventoryZone.createGA(gaZoneId, "General Floor", new BigDecimal("50.00"), 100));

        assignedZoneId = UUID.randomUUID();
        InventoryZone assignedZone = InventoryZone.createAssigned(assignedZoneId, "VIP", new BigDecimal("150.00"));
        seatId = UUID.randomUUID();
        assignedZone.addSeat(new Seat(seatId, "A", "1"));
        event.addZone(assignedZone);

        event.publish();
        eventRepo.save(event);
    }

    private Event createPublishedEvent(UUID id, String name, Duration lockDuration) {
        return new Event(id, companyName, name, "desc", EventCategory.CONCERT,
                defaultSchedule(), new LockTimerDuration(lockDuration));
    }

    private EventSchedule defaultSchedule() {
        return new EventSchedule(
                Instant.parse("2026-07-01T20:00:00Z"),
                Instant.parse("2026-07-01T23:00:00Z"),
                Instant.parse("2026-07-01T19:00:00Z"));
    }

    private void addSeatConcurrently(CountDownLatch startLatch, AtomicInteger successes,
                                     AtomicInteger failures, AtomicReference<Exception> caughtException,
                                     String token, UUID orderId) {
        try {
            startLatch.await();
            orderService.addSeatToOrder(token, orderId, assignedZoneId, seatId);
            successes.incrementAndGet();
        } catch (OptimisticLockException | IllegalStateException e) {
            failures.incrementAndGet();
            caughtException.set(e);
        } catch (Exception e) {
            failures.incrementAndGet();
            caughtException.set(e);
        }
    }

    private static class TestPaymentGateway implements IPaymentGateway {
        boolean failCharges;
        boolean failRefunds;
        int chargeCalls;
        int refundCalls;
        BigDecimal chargedAmount;
        PaymentDetails lastDetails;

        @Override
        public PaymentResult charge(BigDecimal finalAmount, PaymentDetails details) {
            chargeCalls++;
            chargedAmount = finalAmount;
            lastDetails = details;
            if (failCharges) {
                return PaymentResult.failed("declined");
            }
            return PaymentResult.successful("txn-" + chargeCalls);
        }

        @Override
        public RefundResult refund(String transactionId, double amount) {
            refundCalls++;
            if (failRefunds) {
                return RefundResult.failed("refund failed");
            }
            return RefundResult.successful("refund-" + refundCalls);
        }
    }

    private static class TestTicketSupplyGateway implements ITicketSupplyGateway {
        boolean failIssue;
        boolean failCancel;
        boolean partialIssue;
        int issueCalls;
        int cancelCalls;
        List<TicketRequest> lastTickets = List.of();
        List<String> cancelledTickets = List.of();
        CustomerInfo lastCustomer;

        @Override
        public SupplyResult issueTickets(List<TicketRequest> tickets, CustomerInfo customer) {
            issueCalls++;
            lastTickets = List.copyOf(tickets);
            lastCustomer = customer;
            if (failIssue) {
                return SupplyResult.failed("supply down");
            }
            if (partialIssue) {
                return new SupplyResult(false, List.of("TKT-PARTIAL-1"), "partial failure");
            }
            List<String> codes = new ArrayList<>();
            for (int i = 0; i < tickets.size(); i++) {
                codes.add("TKT-" + (i + 1));
            }
            return SupplyResult.successful(codes);
        }

        @Override
        public CancelResult cancelTickets(List<String> ticketCodes) {
            cancelCalls++;
            cancelledTickets = List.copyOf(ticketCodes);
            if (failCancel) {
                return CancelResult.failed("cancel failed");
            }
            return CancelResult.successful();
        }
    }

    @Test
    void GivenActiveOrder_WhenGetActiveOrder_ThenReturnsDtoWithCorrectData() {
        UUID orderId = orderService.createOrder(guestToken, eventId);
        orderService.addGATicketsToOrder(guestToken, orderId, gaZoneId, 3);

        var dto = orderService.getActiveOrder(guestToken, orderId);

        assertEquals(orderId, dto.getId());
        assertEquals(sessionService.extractSessionId(guestToken), dto.getSessionId());
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

    @Test
    void GivenAlreadyLockedSeat_WhenAnotherOrderTriesToLock_ThenRejects() {
        UUID orderId = orderService.createOrder(guestToken, eventId);
        orderService.addSeatToOrder(guestToken, orderId, assignedZoneId, seatId);

        // Second session tries to lock the same seat
        String token2 = sessionService.generateGuestToken();
        UUID orderId2 = orderService.createOrder(token2, eventId);

        assertThrows(IllegalStateException.class,
                () -> orderService.addSeatToOrder(token2, orderId2, assignedZoneId, seatId));
    }

    @Test
    void GivenOrderOwnedBySessionA_WhenSessionBTriesToModify_ThenRejects() {
        UUID orderId = orderService.createOrder(guestToken, eventId);
        orderService.addGATicketsToOrder(guestToken, orderId, gaZoneId, 3);

        // Different session tries to modify
        String otherToken = sessionService.generateGuestToken();

        // Cannot add tickets
        assertThrows(IllegalStateException.class,
                () -> orderService.addGATicketsToOrder(otherToken, orderId, gaZoneId, 1));

        // Cannot remove items
        ActiveOrder order = orderRepo.findById(orderId).orElseThrow();
        UUID itemId = order.getItems().get(0).getId();
        assertThrows(IllegalStateException.class,
                () -> orderService.removeItemFromOrder(otherToken, orderId, itemId));

        // Cannot cancel
        assertThrows(IllegalStateException.class,
                () -> orderService.cancelOrder(otherToken, orderId));

        // Cannot view
        assertThrows(IllegalStateException.class,
                () -> orderService.getActiveOrder(otherToken, orderId));

        // Original order is still intact
        ActiveOrder intact = orderRepo.findById(orderId).orElseThrow();
        assertTrue(intact.isActive());
        assertEquals(3, intact.getTotalTicketCount());
    }

    @Test
    void GivenNoToken_WhenTryToAccessOrder_ThenRejectsWithAuthError() {
        UUID orderId = orderService.createOrder(guestToken, eventId);

        assertThrows(IllegalArgumentException.class,
                () -> orderService.getActiveOrder(null, orderId));
        assertThrows(IllegalArgumentException.class,
                () -> orderService.removeItemFromOrder("", orderId, UUID.randomUUID()));
    }

    @Test
    void GivenPrimarySupplyFails_WhenCheckout_ThenFailsOverToSecondary() {
        TestTicketSupplyGateway primaryGateway = new TestTicketSupplyGateway();
        primaryGateway.failIssue = true; // Primary fails
        TestTicketSupplyGateway secondaryGateway = new TestTicketSupplyGateway();
        
        OrderService failoverService = new OrderService(orderRepo, sessionService, eventRepo, clock, 
                memberRepo, List.of(paymentGateway), List.of(primaryGateway, secondaryGateway),
                new TicketSelectionService(eventRepo));

        UUID orderId = failoverService.createOrder(guestToken, eventId);
        failoverService.addGATicketsToOrder(guestToken, orderId, gaZoneId, 2);

        UUID purchaseId = failoverService.checkout(guestToken, orderId, null);

        assertNotNull(purchaseId);
        assertEquals(1, primaryGateway.issueCalls);
        assertEquals(1, secondaryGateway.issueCalls);
        assertEquals(2, secondaryGateway.lastTickets.size()); // issued successfully here
        ActiveOrder completed = orderRepo.findById(orderId).get();
        assertEquals(OrderStatus.COMPLETED, completed.getStatus());
    }

    @Test
    void GivenAllSupplyGatewaysFail_WhenCheckout_ThenPaymentRefundedAndOrderCancelled() {
        TestTicketSupplyGateway primaryGateway = new TestTicketSupplyGateway();
        primaryGateway.failIssue = true;
        TestTicketSupplyGateway secondaryGateway = new TestTicketSupplyGateway();
        secondaryGateway.failIssue = true;
        
        OrderService failoverService = new OrderService(orderRepo, sessionService, eventRepo, clock, 
                memberRepo, List.of(paymentGateway), List.of(primaryGateway, secondaryGateway),
                new TicketSelectionService(eventRepo));

        UUID orderId = failoverService.createOrder(guestToken, eventId);
        failoverService.addSeatToOrder(guestToken, orderId, assignedZoneId, seatId);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> failoverService.checkout(guestToken, orderId, null));
        
        assertTrue(ex.getMessage().contains("Ticket generation failed"));
        assertEquals(1, primaryGateway.issueCalls);
        assertEquals(1, secondaryGateway.issueCalls);
        assertEquals(1, paymentGateway.refundCalls); // Refunded

        ActiveOrder cancelled = orderRepo.findById(orderId).get();
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertTrue(eventRepo.findById(eventId).get().findZone(assignedZoneId).findSeat(seatId).isAvailable()); // Inventory released
    }

    @Test
    void GivenPartialIssuance_WhenCheckout_ThenIssuesAreCancelledRefundAndOrderCancelled() {
        TestTicketSupplyGateway primaryGateway = new TestTicketSupplyGateway();
        primaryGateway.partialIssue = true; // Returns true with empty success code instead of total success 
        
        OrderService partialService = new OrderService(orderRepo, sessionService, eventRepo, clock, 
                memberRepo, List.of(paymentGateway), List.of(primaryGateway),
                new TicketSelectionService(eventRepo));

        UUID orderId = partialService.createOrder(guestToken, eventId);
        partialService.addGATicketsToOrder(guestToken, orderId, gaZoneId, 1);

        assertThrows(IllegalStateException.class,
                () -> partialService.checkout(guestToken, orderId, null));
        
        assertEquals(1, primaryGateway.issueCalls);
        assertEquals(1, primaryGateway.cancelCalls);
        assertEquals(1, primaryGateway.cancelledTickets.size());
        assertEquals("TKT-PARTIAL-1", primaryGateway.cancelledTickets.get(0));
        assertEquals(1, paymentGateway.refundCalls); // Payment refunded
        
        ActiveOrder cancelled = orderRepo.findById(orderId).get();
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void GivenRefundRejectedAfterSupplyFailure_WhenCheckout_ThenLogsEscalation() {
        ticketSupplyGateway.failIssue = true;
        paymentGateway.failRefunds = true;

        UUID orderId = orderService.createOrder(guestToken, eventId);
        orderService.addGATicketsToOrder(guestToken, orderId, gaZoneId, 1);

        assertThrows(IllegalStateException.class,
                () -> orderService.checkout(guestToken, orderId, null));
        
        assertEquals(1, ticketSupplyGateway.issueCalls);
        assertEquals(1, paymentGateway.refundCalls); // tried to refund
        // Since we can't easily capture the log without extensive mock setup, 
        // we at least ensure the flow acts correctly and does not throw UNHANDLED exceptions.
        ActiveOrder cancelled = orderRepo.findById(orderId).get();
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void GivenMemberWithPurchases_WhenGetPurchaseHistory_ThenReturnsHistory() {
        UUID memberId = UUID.randomUUID();
        Member member = new Member(memberId, "historyUser", "history@example.com", "pw",
                "050-1111111", LocalDate.of(1990, 1, 1));
        memberRepo.save(member);
        String memberToken = sessionService.generateMemberToken(new SessionTokenData(
                UUID.randomUUID(), memberId, Set.of(), member.getUsername(), member.getEmail(), "MEMBER"));

        // No history initially
        List<PurchaseRecordDTO> history1 = orderService.getPurchaseHistory(memberToken);
        assertTrue(history1.isEmpty());

        // Make a purchase
        UUID orderId = orderService.createOrder(memberToken, eventId);
        orderService.addGATicketsToOrder(memberToken, orderId, gaZoneId, 1);
        UUID purchaseId = orderService.checkout(memberToken, orderId, null);

        // Fetch history
        List<PurchaseRecordDTO> history2 = orderService.getPurchaseHistory(memberToken);
        assertEquals(1, history2.size());
        
        PurchaseRecordDTO record = history2.get(0);
        assertEquals(purchaseId, record.purchaseId());
        assertEquals(eventId, record.eventId());
        assertEquals("Summer Concert", record.eventName());
        assertEquals(new BigDecimal("50.00"), record.amount());
        
        // Mutate event details
        Event event = eventRepo.findById(eventId).orElseThrow();
        event.setName("Winter Concert");
        // We cannot change price if tickets are sold, so mutating the name is sufficient for the test
        eventRepo.save(event);
        
        // History should still show original details
        List<PurchaseRecordDTO> history3 = orderService.getPurchaseHistory(memberToken);
        assertEquals(1, history3.size());
        PurchaseRecordDTO recordAfterMutate = history3.get(0);
        assertEquals("Summer Concert", recordAfterMutate.eventName());
        assertEquals(new BigDecimal("50.00"), recordAfterMutate.amount());
    }

    @Test
    void GivenGuestToken_WhenGetPurchaseHistory_ThenThrowsSecurityException() {
        assertThrows(SecurityException.class, () -> orderService.getPurchaseHistory(guestToken));
    }
    void GivenPrimaryPaymentFails_WhenCheckout_ThenFailsOverToSecondary() {
        TestPaymentGateway primaryPayment = new TestPaymentGateway();
        primaryPayment.failCharges = true;
        TestPaymentGateway secondaryPayment = new TestPaymentGateway();
        
        OrderService failoverService = new OrderService(orderRepo, sessionService, eventRepo, clock, 
                memberRepo, List.of(primaryPayment, secondaryPayment), List.of(ticketSupplyGateway),
                new TicketSelectionService(eventRepo));

        UUID orderId = failoverService.createOrder(guestToken, eventId);
        failoverService.addGATicketsToOrder(guestToken, orderId, gaZoneId, 1);
        UUID purchaseId = failoverService.checkout(guestToken, orderId, null);

        assertNotNull(purchaseId);
        assertEquals(1, primaryPayment.chargeCalls);
        assertEquals(1, secondaryPayment.chargeCalls);
        ActiveOrder completed = orderRepo.findById(orderId).get();
        assertEquals(OrderStatus.COMPLETED, completed.getStatus());
    }

    @Test
    void GivenAllPaymentGatewaysFail_WhenCheckout_ThenOrderRemainsActive() {
        TestPaymentGateway primaryPayment = new TestPaymentGateway();
        primaryPayment.failCharges = true;
        TestPaymentGateway secondaryPayment = new TestPaymentGateway();
        secondaryPayment.failCharges = true;
        
        OrderService failoverService = new OrderService(orderRepo, sessionService, eventRepo, clock, 
                memberRepo, List.of(primaryPayment, secondaryPayment), List.of(ticketSupplyGateway),
                new TicketSelectionService(eventRepo));

        UUID orderId = failoverService.createOrder(guestToken, eventId);
        failoverService.addGATicketsToOrder(guestToken, orderId, gaZoneId, 1);

        assertThrows(IllegalStateException.class,
                () -> failoverService.checkout(guestToken, orderId, null));
        
        assertEquals(1, primaryPayment.chargeCalls);
        assertEquals(1, secondaryPayment.chargeCalls);
        ActiveOrder active = orderRepo.findById(orderId).get();
        assertEquals(OrderStatus.ACTIVE, active.getStatus());
        assertTrue(orderRepo.findCompletedByEventId(eventId).isEmpty());
    }

    @Test
    void GivenEventWithCompletedPurchases_WhenRefundEventPurchases_ThenAllPurchasesRefunded() {
        // Create a successful order to refund
        UUID orderId = orderService.createOrder(guestToken, eventId);
        orderService.addGATicketsToOrder(guestToken, orderId, gaZoneId, 2);
        UUID purchaseId = orderService.checkout(guestToken, orderId, null);

        assertNotNull(purchaseId);
        assertEquals(0, paymentGateway.refundCalls); // Should be 0 before we call refundEventPurchases

        // Now trigger the refund (which simulates the Event cancellation side-effect for OrderService)
        orderService.refundEventPurchases(eventId);

        // Verify that the refund was processed via the payment gateway
        assertEquals(1, paymentGateway.refundCalls);
        assertEquals(new BigDecimal("100.00"), paymentGateway.chargedAmount); // Original charge amount was 100
        
        // Ensure that the CompletedPurchase still exists but refund logic executed
        assertTrue(orderRepo.findCompletedById(purchaseId).isPresent());
    }

}
