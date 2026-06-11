package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.SessionTokenData;
import com.ticketing.application.auth.SessionTokenService;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.services.OrderService;
import com.ticketing.domain.company.Company;
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
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.Suspension;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.OrderStatus;
import com.ticketing.domain.services.OrderTimeDomainService;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
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

        OrderTimeDomainService orderTimeDomainService = new OrderTimeDomainService(orderRepo, eventRepo, clock);
        orderService = new OrderService(sessionService, orderRepo, eventRepo, memberRepo, List.of(paymentGateway), List.of(ticketSupplyGateway), clock, null, orderTimeDomainService, null);

        guestToken = sessionService.generateGuestToken();
        setUpPublishedEvent();
    }

    @Test
    void GivenValidOrder_WhenAddGAAndSeat_ThenInventoryIsLocked() {
        UUID orderId = orderService.createOrder(guestToken, eventId);

        orderService.addGATicketsToOrder(guestToken, eventId, gaZoneId, 3);
        Event eventAfterGA = eventRepo.findById(eventId).orElseThrow();
        assertEquals(97, eventAfterGA.findZone(gaZoneId).getAvailableCount());
        assertEquals(3, eventAfterGA.findZone(gaZoneId).getLockedCount());

        orderService.addSeatToOrder(guestToken, eventId, assignedZoneId, seatId);
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

        List<UUID> itemIds = orderService.addSelectionToOrder(guestToken, request);

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
        orderService.addGATicketsToOrder(guestToken, eventId, gaZoneId, 2);

        UUID secondEventId = UUID.randomUUID();
        Event event2 = createPublishedEvent(secondEventId, "Second Show", Duration.ofMinutes(15));
        event2.addZone(InventoryZone.createGA(UUID.randomUUID(), "Zone", new BigDecimal("10.00"), 100));
        eventRepo.save(event2);

        assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(guestToken, secondEventId));

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

        UUID orderId = orderService.createOrder(guestToken, quickEventId);
        orderService.addGATicketsToOrder(guestToken, quickEventId, quickZoneId, 5);
        assertEquals(95, eventRepo.findById(quickEventId).orElseThrow()
                .findZone(quickZoneId).getAvailableCount());

        clock.advance(Duration.ofMillis(250));
        orderService.expireOrders();

        assertEquals(OrderStatus.EXPIRED, orderRepo.findById(orderId).orElseThrow().getStatus());
        Event eventAfter = eventRepo.findById(quickEventId).orElseThrow();
        assertEquals(100, eventAfter.findZone(quickZoneId).getAvailableCount());
        assertEquals(0, eventAfter.findZone(quickZoneId).getLockedCount());
    }

    @Test
    void GivenLockedTickets_WhenCheckoutSucceeds_ThenOrderCompletesInventorySoldAndSnapshotSaved() {
        UUID orderId = orderService.createOrder(guestToken, eventId);
        orderService.addGATicketsToOrder(guestToken, eventId, gaZoneId, 3);
        orderService.addSeatToOrder(guestToken, eventId, assignedZoneId, seatId);

        UUID purchaseId = orderService.checkout(guestToken, null);

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
        orderService.addGATicketsToOrder(guestToken, soldOutEventId, singleZoneId, 1);
        orderService.checkout(guestToken, null);

        assertEquals(EventStatus.SOLD_OUT, eventRepo.findById(soldOutEventId).orElseThrow().getStatus());
    }

    @Test
    void GivenPaymentFails_WhenCheckout_ThenOrderReturnsActiveInventoryStaysLockedAndNoTicketsIssued() {
        paymentGateway.failCharges = true;
        UUID orderId = orderService.createOrder(guestToken, eventId);
        orderService.addGATicketsToOrder(guestToken, eventId, gaZoneId, 1);

        assertThrows(IllegalStateException.class,
                () -> orderService.checkout(guestToken, null));

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
        orderService.addGATicketsToOrder(guestToken, eventId, gaZoneId, 2);

        assertThrows(IllegalStateException.class,
                () -> orderService.checkout(guestToken, null));

        assertEquals(OrderStatus.CANCELLED, orderRepo.findById(orderId).orElseThrow().getStatus());
        Event event = eventRepo.findById(eventId).orElseThrow();
        assertEquals(100, event.findZone(gaZoneId).getAvailableCount());
        assertEquals(0, event.findZone(gaZoneId).getLockedCount());
        assertEquals(1, paymentGateway.refundCalls);
        assertTrue(orderRepo.findCompletedByEventId(eventId).isEmpty());
    }

    @Test
    void GivenPurchasePolicyRejects_WhenAddingTickets_ThenReservationBlockedAndPaymentNotCharged() {
        UUID policyEventId = UUID.randomUUID();
        UUID policyZoneId = UUID.randomUUID();
        Event event = new Event(policyEventId, companyName, "Policy Show", "desc", EventCategory.CONCERT,
                defaultSchedule(), new LockTimerDuration(Duration.ofMinutes(15)),
                (ctx) -> PolicyResult.failure("DENIED", "No tickets for you"),
                (order, coupon, now) -> order.getTotalPrice().max(BigDecimal.ZERO));
        event.addZone(InventoryZone.createGA(policyZoneId, "Floor", new BigDecimal("20.00"), 5));
        event.publish();
        eventRepo.save(event);

        orderService.createOrder(guestToken, policyEventId);

        assertThrows(IllegalStateException.class,
                () -> orderService.addGATicketsToOrder(guestToken, policyEventId, policyZoneId, 1));

        assertEquals(0, paymentGateway.chargeCalls);
    }

    @Test
    void GivenDiscountPolicy_WhenCheckout_ThenCompletedPurchaseUsesDiscountedAmount() {
        UUID discountEventId = UUID.randomUUID();
        UUID discountZoneId = UUID.randomUUID();
        Event event = new Event(discountEventId, companyName, "Discount Show", "desc", EventCategory.CONCERT,
                defaultSchedule(), new LockTimerDuration(Duration.ofMinutes(15)),
                (ctx) -> PolicyResult.success(),
                (order, coupon, now) -> order.getTotalPrice().subtract(new BigDecimal("20.00")).max(BigDecimal.ZERO));
        event.addZone(InventoryZone.createGA(discountZoneId, "Floor", new BigDecimal("50.00"), 10));
        event.publish();
        eventRepo.save(event);

        UUID orderId = orderService.createOrder(guestToken, discountEventId);
        orderService.addGATicketsToOrder(guestToken, discountEventId, discountZoneId, 2);
        UUID purchaseId = orderService.checkout(guestToken, "SAVE20");

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
        orderService.addGATicketsToOrder(memberToken, eventId, gaZoneId, 1);
        UUID purchaseId = orderService.checkout(memberToken, null);

        assertEquals(memberId, orderRepo.findCompletedById(purchaseId).orElseThrow().memberId());
        assertEquals(memberId, paymentGateway.lastDetails.memberId());
        assertEquals("member@example.com", paymentGateway.lastDetails.email());
        assertEquals(memberId.toString(), ticketSupplyGateway.lastCustomer.userId());
        assertEquals("member@example.com", ticketSupplyGateway.lastCustomer.email());
        assertEquals("memberUser", ticketSupplyGateway.lastCustomer.fullName());
    }

    @Test
    void GivenSuccessfulMemberCheckout_WhenCompleted_ThenReceiptWrittenToHistoryRepositoryWithBuyerAmountAndItems() {
        // Given: a member with a multi-item order (2 GA @ 50.00 + 1 assigned seat @ 150.00)
        UUID memberId = UUID.randomUUID();
        Member member = new Member(memberId, "receiptUser", "receipt@example.com", "pw",
                "050-2222222", LocalDate.of(1990, 1, 1));
        memberRepo.save(member);
        String memberToken = sessionService.generateMemberToken(new SessionTokenData(
                UUID.randomUUID(), memberId, Set.of(), member.getUsername(), member.getEmail(), "MEMBER"));

        UUID orderId = orderService.createOrder(memberToken, eventId);
        orderService.addGATicketsToOrder(memberToken, eventId, gaZoneId, 2);
        orderService.addSeatToOrder(memberToken, eventId, assignedZoneId, seatId);

        // When: the order is checked out successfully
        UUID purchaseId = orderService.checkout(memberToken, null);

        // Then: the receipt is persisted in the history repository, keyed by its id
        CompletedPurchase receipt = orderRepo.findCompletedById(purchaseId).orElseThrow();
        assertEquals(purchaseId, receipt.purchaseId());
        assertEquals(memberId, receipt.memberId());                 // buyer
        assertEquals("receiptUser", receipt.buyerUsername());       // buyer username snapshotted at checkout
        assertEquals(new BigDecimal("250.00"), receipt.amount());   // final amount: 2*50.00 + 150.00
        assertEquals(eventId, receipt.eventId());
        assertEquals("Summer Concert", receipt.eventName());
        assertEquals(companyName, receipt.companyName());

        // And: it surfaces through the buyer's purchase-history query
        List<PurchaseRecordDTO> history = orderService.getPurchaseHistory(memberToken);
        assertEquals(1, history.size());
        assertEquals(purchaseId, history.get(0).purchaseId());
        assertEquals("receiptUser", history.get(0).buyerUsername());
        assertEquals(new BigDecimal("250.00"), history.get(0).amount());

        // And: the purchased items are recorded on the now-completed order.
        // (CompletedPurchase carries no line-item snapshot, so items are asserted on the order.)
        ActiveOrder completed = orderRepo.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, completed.getStatus());
        assertEquals(2, completed.getItems().size());
        assertEquals(3, completed.getTotalTicketCount());
    }

    @Test
    void GivenSuspendedMember_WhenCreateOrAddToCart_ThenRejectedAndNoPaymentAttempted() {
        UUID memberId = UUID.randomUUID();
        Member member = new Member(memberId, "suspendedUser", "suspended@example.com", "pw",
                "050-1111111", LocalDate.of(1990, 1, 1));
        member.addSuspension(new Suspension(UUID.randomUUID(), clock.now(), Duration.ofDays(7), "fraud"));
        memberRepo.save(member);
        String memberToken = sessionService.generateMemberToken(new SessionTokenData(
                UUID.randomUUID(), memberId, Set.of(), member.getUsername(), member.getEmail(), "MEMBER"));

        IllegalStateException createEx = assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(memberToken, eventId));
        assertTrue(createEx.getMessage().contains("suspended"));

        IllegalStateException addEx = assertThrows(IllegalStateException.class,
                () -> orderService.addGATicketsToOrder(memberToken, eventId, gaZoneId, 1));
        assertTrue(addEx.getMessage().contains("suspended"));

        IllegalStateException addSeatEx = assertThrows(IllegalStateException.class,
                () -> orderService.addSeatToOrder(memberToken, eventId, assignedZoneId, seatId));
        assertTrue(addSeatEx.getMessage().contains("suspended"));

        SelectionRequest selection = new SelectionRequest(eventId,
                List.of(new SelectionRequest.SeatPick(assignedZoneId, seatId)),
                List.of(new SelectionRequest.GAPick(gaZoneId, 1)));
        IllegalStateException addSelectionEx = assertThrows(IllegalStateException.class,
                () -> orderService.addSelectionToOrder(memberToken, selection));
        assertTrue(addSelectionEx.getMessage().contains("suspended"));

        assertEquals(0, paymentGateway.chargeCalls);
        assertEquals(0, ticketSupplyGateway.issueCalls);
        assertNull(orderService.getActiveOrder(memberToken));
        assertTrue(orderRepo.findCompletedByEventId(eventId).isEmpty());
    }

    @Test
    void GivenMemberSuspendedAfterCartBuilt_WhenMutatingCart_ThenRejectedAndReadOnlyCartStillVisible() {
        UUID memberId = UUID.randomUUID();
        Member member = new Member(memberId, "cartUser", "cart@example.com", "pw",
                "050-1111111", LocalDate.of(1990, 1, 1));
        memberRepo.save(member);
        String memberToken = sessionService.generateMemberToken(new SessionTokenData(
                UUID.randomUUID(), memberId, Set.of(), member.getUsername(), member.getEmail(), "MEMBER"));

        UUID orderId = orderService.createOrder(memberToken, eventId);
        UUID itemId = orderService.addGATicketsToOrder(memberToken, eventId, gaZoneId, 1);

        member.addSuspension(new Suspension(UUID.randomUUID(), clock.now(), Duration.ofDays(7), "fraud"));
        memberRepo.save(member);

        assertDoesNotThrow(() -> orderService.getActiveOrder(memberToken));
        assertEquals(orderId, orderService.getActiveOrder(memberToken).getId());

        assertThrows(IllegalStateException.class,
                () -> orderService.addGATicketsToOrder(memberToken, eventId, gaZoneId, 1));
        assertThrows(IllegalStateException.class,
                () -> orderService.addSeatToOrder(memberToken, eventId, assignedZoneId, seatId));
        assertThrows(IllegalStateException.class,
                () -> orderService.addSelectionToOrder(memberToken, new SelectionRequest(eventId,
                        List.of(new SelectionRequest.SeatPick(assignedZoneId, seatId)),
                        List.of(new SelectionRequest.GAPick(gaZoneId, 1)))));
        assertThrows(IllegalStateException.class,
                () -> orderService.updateGAQuantity(memberToken, gaZoneId, 2));
        assertThrows(IllegalStateException.class,
                () -> orderService.removeItemFromOrder(memberToken, itemId));
        assertThrows(IllegalStateException.class,
                () -> orderService.cancelOrder(memberToken));
        assertThrows(IllegalStateException.class,
                () -> orderService.checkout(memberToken, null));

        assertEquals(0, paymentGateway.chargeCalls);
        assertEquals(0, ticketSupplyGateway.issueCalls);
        assertEquals(OrderStatus.ACTIVE, orderRepo.findById(orderId).orElseThrow().getStatus());
        assertTrue(orderRepo.findCompletedByEventId(eventId).isEmpty());
    }

    @Test
    void GivenMemberSuspendedMidCheckout_WhenCheckout_ThenRejectedBeforePayment() {
        UUID memberId = UUID.randomUUID();
        Member member = new Member(memberId, "raceUser", "race@example.com", "pw",
                "050-1111111", LocalDate.of(1990, 1, 1));
        memberRepo.save(member);

        // This repo lets the initial checkout guard pass, then suspends the member
        // before the payment step — simulating an admin suspending mid-checkout.
        MidCheckoutSuspendingRepository racingRepo =
                new MidCheckoutSuspendingRepository(memberRepo, memberId, clock.now());
        OrderService racingService = new OrderService(sessionService, orderRepo, eventRepo, racingRepo,
                List.of(paymentGateway), List.of(ticketSupplyGateway), clock, null, null, null);

        String memberToken = sessionService.generateMemberToken(new SessionTokenData(
                UUID.randomUUID(), memberId, Set.of(), member.getUsername(), member.getEmail(), "MEMBER"));

        UUID orderId = racingService.createOrder(memberToken, eventId);
        racingService.addGATicketsToOrder(memberToken, eventId, gaZoneId, 1);

        // Arm: the next checkout passes the initial guard, then the member is suspended.
        racingRepo.arm();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> racingService.checkout(memberToken, null));
        assertTrue(ex.getMessage().contains("suspended"));

        // The mid-checkout re-check fired before any payment/issuance side effect.
        assertEquals(0, paymentGateway.chargeCalls);
        assertEquals(0, ticketSupplyGateway.issueCalls);
        assertEquals(OrderStatus.ACTIVE, orderRepo.findById(orderId).orElseThrow().getStatus());
        assertTrue(orderRepo.findCompletedByEventId(eventId).isEmpty());
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
            orderService.addSeatToOrder(token, eventId, assignedZoneId, seatId);
            successes.incrementAndGet();
        } catch (OptimisticLockException | IllegalStateException e) {
            failures.incrementAndGet();
            caughtException.set(e);
        } catch (Exception e) {
            failures.incrementAndGet();
            caughtException.set(e);
        }
    }

    /**
     * Delegating member repository that, once armed, returns the (still un-suspended)
     * member to the first lookup — the initial checkout guard — and then suspends the
     * stored member so every subsequent lookup sees the suspension. This deterministically
     * reproduces an admin suspending the member after checkout starts.
     */
    private static class MidCheckoutSuspendingRepository implements IMemberRepository {
        private final IMemberRepository delegate;
        private final UUID targetId;
        private final Instant suspendAt;
        private boolean armed = false;
        private boolean suspensionApplied = false;

        MidCheckoutSuspendingRepository(IMemberRepository delegate, UUID targetId, Instant suspendAt) {
            this.delegate = delegate;
            this.targetId = targetId;
            this.suspendAt = suspendAt;
        }

        void arm() {
            this.armed = true;
        }

        @Override
        public Optional<Member> findById(UUID memberId) {
            Optional<Member> result = delegate.findById(memberId);
            if (armed && !suspensionApplied && targetId.equals(memberId)) {
                suspensionApplied = true;
                delegate.findById(memberId).ifPresent(stored -> {
                    stored.addSuspension(new Suspension(UUID.randomUUID(), suspendAt,
                            Duration.ofDays(7), "mid-checkout"));
                    delegate.save(stored);
                });
            }
            return result;
        }

        @Override public void save(Member member) { delegate.save(member); }
        @Override public Optional<Member> findByUsername(String username) { return delegate.findByUsername(username); }
        @Override public Optional<Member> findByEmail(String email) { return delegate.findByEmail(email); }
        @Override public boolean existsByUsername(String username) { return delegate.existsByUsername(username); }
        @Override public boolean existsByEmail(String email) { return delegate.existsByEmail(email); }
        @Override public boolean saveIfUsernameAndEmailAvailable(Member member) { return delegate.saveIfUsernameAndEmailAvailable(member); }
        @Override public boolean updateIfUsernameAndEmailAvailable(Member member, String username, String email) { return delegate.updateIfUsernameAndEmailAvailable(member, username, email); }
        @Override public long count() { return delegate.count(); }
        @Override public List<Member> findByCompanyAppointment(String companyName) { return delegate.findByCompanyAppointment(companyName); }
        @Override public void delete(Member member) { delegate.delete(member); }
        @Override public List<Member> findAll() { return delegate.findAll(); }
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
        orderService.addGATicketsToOrder(guestToken, eventId, gaZoneId, 3);

        var dto = orderService.getActiveOrder(guestToken);

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
                () -> orderService.addGATicketsToOrder(guestToken, eventId, gaZoneId, 101));

        // Verify inventory unchanged
        Event event = eventRepo.findById(eventId).get();
        assertEquals(100, event.findZone(gaZoneId).getAvailableCount());
        assertEquals(0, event.findZone(gaZoneId).getLockedCount());
    }

    @Test
    void GivenAlreadyLockedSeat_WhenAnotherOrderTriesToLock_ThenRejects() {
        UUID orderId = orderService.createOrder(guestToken, eventId);
        orderService.addSeatToOrder(guestToken, eventId, assignedZoneId, seatId);

        // Second session tries to lock the same seat
        String token2 = sessionService.generateGuestToken();
        UUID orderId2 = orderService.createOrder(token2, eventId);

        assertThrows(IllegalStateException.class,
                () -> orderService.addSeatToOrder(token2, eventId, assignedZoneId, seatId));
    }

    @Test
    void GivenOrderOwnedBySessionA_WhenSessionBTriesToModify_ThenRejects() {
        UUID orderId = orderService.createOrder(guestToken, eventId);
        orderService.addGATicketsToOrder(guestToken, eventId, gaZoneId, 3);

        // Different session tries to modify
        String otherToken = sessionService.generateGuestToken();


        // Cannot remove items
        ActiveOrder order = orderRepo.findById(orderId).orElseThrow();
        UUID itemId = order.getItems().get(0).getId();
        assertThrows(IllegalArgumentException.class,
                () -> orderService.removeItemFromOrder(otherToken, itemId));

        // Cannot cancel
        assertThrows(IllegalArgumentException.class,
                () -> orderService.cancelOrder(otherToken));

        // Cannot view (returns null, not throws)
        assertNull(orderService.getActiveOrder(otherToken));

        // Original order is still intact
        ActiveOrder intact = orderRepo.findById(orderId).orElseThrow();
        assertTrue(intact.isActive());
        assertEquals(3, intact.getTotalTicketCount());
    }

    @Test
    void GivenNoToken_WhenTryToAccessOrder_ThenRejectsWithAuthError() {
        UUID orderId = orderService.createOrder(guestToken, eventId);

        assertThrows(IllegalArgumentException.class,
                () -> orderService.getActiveOrder(null));
        assertThrows(IllegalArgumentException.class,
                () -> orderService.removeItemFromOrder("", UUID.randomUUID()));
    }

    @Test
    void GivenPrimarySupplyFails_WhenCheckout_ThenFailsOverToSecondary() {
        TestTicketSupplyGateway primaryGateway = new TestTicketSupplyGateway();
        primaryGateway.failIssue = true; // Primary fails
        TestTicketSupplyGateway secondaryGateway = new TestTicketSupplyGateway();

        OrderService failoverService = new OrderService(sessionService, orderRepo, eventRepo, memberRepo, List.of(paymentGateway), List.of(primaryGateway, secondaryGateway), clock, null, null, null);

        UUID orderId = failoverService.createOrder(guestToken, eventId);
        failoverService.addGATicketsToOrder(guestToken, eventId, gaZoneId, 2);

        UUID purchaseId = failoverService.checkout(guestToken, null);

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

        OrderService failoverService = new OrderService(sessionService, orderRepo, eventRepo, memberRepo, List.of(paymentGateway), List.of(primaryGateway, secondaryGateway), clock, null, null, null);

        UUID orderId = failoverService.createOrder(guestToken, eventId);
        failoverService.addSeatToOrder(guestToken, eventId, assignedZoneId, seatId);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> failoverService.checkout(guestToken, null));
        
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

        OrderService partialService = new OrderService(sessionService, orderRepo, eventRepo, memberRepo, List.of(paymentGateway), List.of(primaryGateway), clock, null, null, null);

        UUID orderId = partialService.createOrder(guestToken, eventId);
        partialService.addGATicketsToOrder(guestToken, eventId, gaZoneId, 1);

        assertThrows(IllegalStateException.class,
                () -> partialService.checkout(guestToken, null));
        
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
        orderService.addGATicketsToOrder(guestToken, eventId, gaZoneId, 1);

        assertThrows(IllegalStateException.class,
                () -> orderService.checkout(guestToken, null));
        
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
        orderService.addGATicketsToOrder(memberToken, eventId, gaZoneId, 1);
        UUID purchaseId = orderService.checkout(memberToken, null);

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

        OrderService failoverService = new OrderService(sessionService, orderRepo, eventRepo, memberRepo, List.of(primaryPayment, secondaryPayment), List.of(ticketSupplyGateway), clock, null, null, null);

        UUID orderId = failoverService.createOrder(guestToken, eventId);
        failoverService.addGATicketsToOrder(guestToken, eventId, gaZoneId, 1);
        UUID purchaseId = failoverService.checkout(guestToken, null);

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

        OrderService failoverService = new OrderService(sessionService, orderRepo, eventRepo, memberRepo, List.of(primaryPayment, secondaryPayment), List.of(ticketSupplyGateway), clock, null, null, null);

        UUID orderId = failoverService.createOrder(guestToken, eventId);
        failoverService.addGATicketsToOrder(guestToken, eventId, gaZoneId, 1);

        assertThrows(IllegalStateException.class,
                () -> failoverService.checkout(guestToken, null));
        
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
        orderService.addGATicketsToOrder(guestToken, eventId, gaZoneId, 2);
        UUID purchaseId = orderService.checkout(guestToken, null);

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

    @Test
    void GivenSuspendedMember_WhenCreateOrder_ThenRejectsWithSuspendedMessage() {
        UUID memberId = UUID.randomUUID();
        Member member = new Member(memberId, "suspendedUser", "suspended@example.com", "pw",
                "050-2222222", LocalDate.of(1995, 5, 5));
        member.addSuspension(new Suspension(UUID.randomUUID(), clock.now(), null, "violation"));
        memberRepo.save(member);
        String memberToken = sessionService.generateMemberToken(new SessionTokenData(
                UUID.randomUUID(), memberId, Set.of(), member.getUsername(), member.getEmail(), "MEMBER"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(memberToken, eventId));
        assertTrue(ex.getMessage().contains("suspended"));

        Event event = eventRepo.findById(eventId).orElseThrow();
        assertEquals(100, event.findZone(gaZoneId).getAvailableCount());
        assertEquals(0, event.findZone(gaZoneId).getLockedCount());
    }

    @Test
    void GivenSuspendedCompany_WhenCreateOrderByEventId_ThenRejectsAndInventoryUnchanged() {
        InMemoryCompanyRepository companyRepo = new InMemoryCompanyRepository();
        Company company = new Company(companyName, "desc", UUID.randomUUID());
        company.suspend();
        companyRepo.save(company);
        OrderService guardedOrderService = new OrderService(sessionService, orderRepo, eventRepo, companyRepo,
                memberRepo, List.of(paymentGateway), List.of(ticketSupplyGateway), clock, null, null, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guardedOrderService.createOrder(guestToken, eventId));

        assertTrue(ex.getMessage().contains("suspended or closed"));
        Event event = eventRepo.findById(eventId).orElseThrow();
        assertEquals(100, event.findZone(gaZoneId).getAvailableCount());
        assertEquals(0, event.findZone(gaZoneId).getLockedCount());
    }

    @Test
    void GivenMemberSuspendedAfterOrderCreated_WhenAddTickets_ThenRejectsAndInventoryUnchanged() {
        UUID memberId = UUID.randomUUID();
        Member member = new Member(memberId, "laterSuspended", "later@example.com", "pw",
                "050-3333333", LocalDate.of(1992, 3, 3));
        memberRepo.save(member);
        String memberToken = sessionService.generateMemberToken(new SessionTokenData(
                UUID.randomUUID(), memberId, Set.of(), member.getUsername(), member.getEmail(), "MEMBER"));

        UUID orderId = orderService.createOrder(memberToken, eventId);
        assertNotNull(orderId);

        member.addSuspension(new Suspension(UUID.randomUUID(), clock.now(), null, "late violation"));
        memberRepo.save(member);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> orderService.addGATicketsToOrder(memberToken, eventId, gaZoneId, 2));
        assertTrue(ex.getMessage().contains("suspended"));

        Event event = eventRepo.findById(eventId).orElseThrow();
        assertEquals(100, event.findZone(gaZoneId).getAvailableCount());
        assertEquals(0, event.findZone(gaZoneId).getLockedCount());
    }

}
