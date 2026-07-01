package com.ticketing.application.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.application.CardPaymentInfo;
import com.ticketing.application.ISystemClock;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.QueueEntryDto;
import com.ticketing.application.dto.VirtualQueueDto;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.PurchaseContext;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.exception.OptimisticLockException;
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
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.BuyerContactSnapshot;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.order.OrderItem;
import com.ticketing.domain.order.OrderStatus;
import com.ticketing.domain.order.RefundStatus;
import com.ticketing.domain.order.SelectionRequest;
import com.ticketing.domain.queue.IQueueRepository;
import com.ticketing.domain.queue.QueueConfig;
import com.ticketing.domain.queue.QueueEntry;
import com.ticketing.domain.queue.VirtualQueue;
import com.ticketing.domain.services.OrderTimeDomainService;
import com.ticketing.infrastructure.persistence.DatabaseConnectivityProbe;
import com.ticketing.infrastructure.persistence.DbConnectivityFailures;

/**
 * Application service for orders, checkout and the virtual queue.
 *
 * <p>V3-10 (#268): each public use-case method is one atomic, isolated transaction.
 * The class is {@code @Transactional(readOnly = true)} so query use cases run in a
 * read-only transaction by default; mutating use cases override this with a
 * read-write {@code @Transactional}. In {@code jpa} mode the auto-configured
 * {@code JpaTransactionManager} commits the unit at the method boundary and rolls
 * back on any thrown exception; in {@code memory} mode (no Spring proxy in unit
 * tests) the annotations are inert and behavior is unchanged.
 */
@org.springframework.stereotype.Service
@org.springframework.context.annotation.Lazy
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final ISessionTokenService sessionTokenService;
    private final IOrderRepository orderRepository;
    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;
    private final IMemberRepository memberRepository;
    private final List<IPaymentGateway> paymentGateways;
    private final List<ITicketSupplyGateway> ticketSupplyGateways;
    private final ISystemClock systemClock;
    private final IQueueRepository queueRepository;
    private final OrderTimeDomainService orderTimeDomainService;
    private final INotificationService notificationService;
    private final SystemAnalyticsCollector analyticsCollector;

    // V3-13 (#271): per-event virtual-queue defaults are config-driven (ticketing.queue.*).
    // The field initializers are the fallback for `new`-built unit tests, where Spring does
    // not perform @Value injection; in the running app @Value overrides them from config/env.
    @org.springframework.beans.factory.annotation.Value("${ticketing.queue.threshold:100}")
    private int defaultQueueThreshold = 100;
    @org.springframework.beans.factory.annotation.Value("${ticketing.queue.flow-rate:10}")
    private int defaultQueueFlowRate = 10;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DatabaseConnectivityProbe databaseConnectivityProbe;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    public OrderService(ISessionTokenService sessionTokenService,
            IOrderRepository orderRepository,
            IEventRepository eventRepository,
            IMemberRepository memberRepository,
            List<IPaymentGateway> paymentGateways,
            List<ITicketSupplyGateway> ticketSupplyGateways,
            ISystemClock systemClock,
            IQueueRepository queueRepository,
            OrderTimeDomainService orderTimeDomainService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) INotificationService notificationService) {
        this(sessionTokenService, orderRepository, eventRepository, null, memberRepository, paymentGateways,
                ticketSupplyGateways, systemClock, queueRepository, orderTimeDomainService, notificationService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OrderService(ISessionTokenService sessionTokenService,
            IOrderRepository orderRepository,
            IEventRepository eventRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ICompanyRepository companyRepository,
            IMemberRepository memberRepository,
            List<IPaymentGateway> paymentGateways,
            List<ITicketSupplyGateway> ticketSupplyGateways,
            ISystemClock systemClock,
            IQueueRepository queueRepository,
            OrderTimeDomainService orderTimeDomainService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) INotificationService notificationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SystemAnalyticsCollector analyticsCollector) {
        if (sessionTokenService == null)
            throw new IllegalArgumentException("sessionTokenService is required");
        if (orderRepository == null)
            throw new IllegalArgumentException("orderRepository is required");
        if (eventRepository == null)
            throw new IllegalArgumentException("eventRepository is required");

        this.sessionTokenService = sessionTokenService;
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.paymentGateways = paymentGateways != null ? paymentGateways : List.of();
        this.ticketSupplyGateways = ticketSupplyGateways != null ? ticketSupplyGateways : List.of();
        this.systemClock = systemClock;
        this.queueRepository = queueRepository;
        this.orderTimeDomainService = orderTimeDomainService;
        this.notificationService = notificationService;
        this.analyticsCollector = analyticsCollector;
    }

    private boolean isPersistenceDatabaseReady() {
        return databaseConnectivityProbe == null || databaseConnectivityProbe.isReady();
    }

    private void runScheduledMaintenance(Runnable task) {
        try {
            if (transactionManager != null) {
                new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> runScheduledMaintenanceBody(task));
            } else {
                runScheduledMaintenanceBody(task);
            }
        } catch (RuntimeException ex) {
            if (logScheduledDatabaseSkip(ex)) {
                return;
            }
            throw ex;
        }
    }

    private void runScheduledMaintenanceBody(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException ex) {
            if (logScheduledDatabaseSkip(ex)) {
                return;
            }
            throw ex;
        }
    }

    private boolean logScheduledDatabaseSkip(RuntimeException ex) {
        if (!DbConnectivityFailures.isUnavailable(ex) && !DbConnectivityFailures.isDeferrableAtStartup(ex)) {
            return false;
        }
        log.warn("Scheduled maintenance skipped: database unavailable ({})", ex.toString());
        return true;
    }

    // ── Order creation & ticket reservation ─────────────────────────

    @Transactional
    public UUID createOrder(String token, UUID eventId) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        log.info("Create order requested: eventId={}, sessionId={}, memberId={}", eventId, sessionId, memberId);
        rejectIfMemberSuspended(memberId);
        return findOrCreateActiveOrder(sessionId, memberId, eventId).getId();
    }

    @Transactional
    public UUID addSeatToOrder(String token, UUID eventId, UUID zoneId, UUID seatId) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        log.info("Add seat requested: eventId={}, zoneId={}, seatId={}, sessionId={}",
                eventId, zoneId, seatId, sessionId);
        rejectIfMemberSuspended(memberId);
        ActiveOrder order = findOrCreateActiveOrder(sessionId, memberId, eventId);
        validateOrderOwnership(sessionId, order);
        return addSelectionToOrder(sessionId, order,
                new SelectionRequest(order.getEventId(),
                        List.of(new SelectionRequest.SeatPick(zoneId, seatId)),
                        List.of())).get(0);
    }

    @Transactional
    public UUID addGATicketsToOrder(String token, UUID eventId, UUID zoneId, int quantity) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        log.info("Add GA tickets requested: eventId={}, zoneId={}, quantity={}, sessionId={}",
                eventId, zoneId, quantity, sessionId);
        rejectIfMemberSuspended(memberId);
        ActiveOrder order = findOrCreateActiveOrder(sessionId, memberId, eventId);
        validateOrderOwnership(sessionId, order);
        return addSelectionToOrder(sessionId, order,
                new SelectionRequest(order.getEventId(),
                        List.of(),
                        List.of(new SelectionRequest.GAPick(zoneId, quantity)))).get(0);
    }

    @Transactional
    public List<UUID> addSelectionToOrder(String token, com.ticketing.application.SelectionRequest request) {
        validateToken(token);
        if (request == null)
            throw new IllegalArgumentException("request is required");
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        log.info("Add selection requested: eventId={}, sessionId={}, seats={}, gaPicks={}",
                request.eventId(), sessionId, request.seats().size(), request.gaQuantities().size());
        rejectIfMemberSuspended(memberId);
        ActiveOrder order = findOrCreateActiveOrder(sessionId, memberId, request.eventId());
        SelectionRequest domainRequest = new SelectionRequest(
                request.eventId(),
                request.seats().stream()
                        .map(s -> new SelectionRequest.SeatPick(s.zoneId(), s.seatId()))
                        .toList(),
                request.gaQuantities().stream()
                        .map(g -> new SelectionRequest.GAPick(g.zoneId(), g.quantity()))
                        .toList());
        return addSelectionToOrder(sessionId, order, domainRequest);
    }

    @Transactional
    public void removeItemFromOrder(String token, UUID itemId) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        rejectIfMemberSuspended(memberId);

        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order == null) throw new IllegalArgumentException("No active order found");
        validateOrderOwnership(sessionId, order);
        Event event = findEvent(order.getEventId());

        OrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Item not found: itemId={}", itemId);
                    return new IllegalArgumentException("Item not found: " + itemId);
                });

        ActiveOrder orderAfterRemoval = order.simulateWithoutItem(itemId);
        Set<UUID> seatsBecomingAvailable = item.isAssignedSeat() && item.getSeatId() != null
                ? Set.of(item.getSeatId())
                : Set.of();
        requirePurchasePolicyCompliance(event, PurchaseContext.forRemoval(
                event, orderAfterRemoval, memberId, getBuyerDateOfBirth(memberId), seatsBecomingAvailable));

        releaseInventoryForItem(event, item);

        checkAndPublishAvailable(event);
        saveEvent(event);

        order.removeItem(itemId);
        saveOrder(order);
        log.info("Item removed from order: orderId={}, itemId={}", order.getId(), itemId);
    }

    @Transactional
    public void updateGAQuantity(String token, UUID zoneId, int newQuantity) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        rejectIfMemberSuspended(memberId);

        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order == null) throw new IllegalArgumentException("No active order found");
        validateOrderOwnership(sessionId, order);
        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);

        order.findItemByZoneId(zoneId)
                .orElseThrow(() -> new IllegalArgumentException("No GA item found for zone: " + zoneId));

        ActiveOrder orderAfterUpdate = order.simulateGAQuantity(zoneId, newQuantity);
        requirePurchasePolicyCompliance(event, PurchaseContext.forOrder(
                event, orderAfterUpdate, memberId, getBuyerDateOfBirth(memberId)));

        updateGAQuantityInternal(order, event, zoneId, newQuantity);
        checkAndPublishAvailable(event);
        saveEvent(event);
        saveOrder(order);
        log.info("GA quantity updated: orderId={}, zoneId={}", order.getId(), zoneId);
    }

    @Transactional
    public void cancelOrder(String token) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        rejectIfMemberSuspended(memberId);

        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order == null) throw new IllegalArgumentException("No active order found");
        validateOrderOwnership(sessionId, order);
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed order");
        }
        if (order.isLotteryWin()) {
            throw new IllegalStateException(
                    "Lottery win reservation cannot be cancelled. Select tickets on the Events page and checkout, or wait for the window to expire.");
        }

        Event event = findEvent(order.getEventId());
        releaseAllInventory(event, order);
        checkAndPublishAvailable(event);
        saveEvent(event);

        order.cancel();
        saveOrder(order);
        log.info("Order cancelled: orderId={}", order.getId());
    }

    // Read-WRITE despite being a "get": getActiveOrder(sessionId, memberId) lazily
    // reconciles the session/member id and releases inventory for an expired order,
    // so this use case must run in a read-write transaction (not the class-level
    // readOnly default) or those writes would fail to flush in jpa mode.
    @Transactional
    public ActiveOrderDto getActiveOrder(String token) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        log.info("Active order requested: sessionId={}, memberId={}", sessionId, memberId);
        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order == null)
            return null;
        Event event = findEvent(order.getEventId());
        BigDecimal subtotal = order.getTotalPrice();
        BigDecimal total = subtotal;
        try {
            total = event.calculateOrderTotal(order, systemClock.now());
        } catch (IllegalArgumentException e) {
            // Invalid coupon stored? We return subtotal as fallback.
        }

        return new ActiveOrderDto(
                order.getId(),
                order.getSessionId(),
                order.getMemberId(),
                order.getEventId(),
                order.getCreatedAt(),
                order.getStatus().name(),
                order.getItemsDto(),
                subtotal,
                total,
                null,
                order.isLotteryWin(),
                order.getPurchaseWindowDeadline(),
                order.getCouponCode());
    }

    // ── Checkout ────────────────────────────────────────────────────

    /**
     * Estimates the amount that would be charged at checkout for the active order,
     * applying the event discount policy and optional coupon without side effects.
     */
    @Transactional
    public CheckoutQuote quoteCheckout(String token) {
        validateToken(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        log.info("Checkout quote requested: sessionId={}, memberId={}", sessionId, memberId);
        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order == null) {
            throw new IllegalArgumentException("No active order found");
        }
        if (order.getItems().isEmpty()) {
            throw new IllegalArgumentException("No active order with tickets to checkout");
        }
        Event event = findEvent(order.getEventId());
        BigDecimal subtotal = order.getTotalPrice();
        BigDecimal total = event.calculateOrderTotal(order, systemClock.now());
        return new CheckoutQuote(subtotal, total);
    }

    @Transactional
    public void applyCoupon(String token, String couponCode) {
        validateToken(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        log.info("Apply coupon requested: sessionId={}, memberId={}, couponCode={}", sessionId, memberId, couponCode);

        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order == null) {
            throw new IllegalArgumentException("No active order found");
        }
        
        String oldCoupon = order.getCouponCode();
        order.setCouponCode(couponCode);
        
        try {
            Event event = findEvent(order.getEventId());
            event.calculateOrderTotal(order, systemClock.now()); // validate it
            saveOrder(order);
        } catch (IllegalArgumentException e) {
            order.setCouponCode(oldCoupon); // rollback in memory
            throw e;
        }
    }

    public record CheckoutQuote(BigDecimal subtotal, BigDecimal total) {
    }

    public record PurchasePolicyStatus(boolean compliant, String reason) {
        public static PurchasePolicyStatus ok() {
            return new PurchasePolicyStatus(true, "");
        }

        public static PurchasePolicyStatus violation(String reason) {
            return new PurchasePolicyStatus(false, reason == null ? "" : reason);
        }
    }

    /**
     * Read-only check whether the active order satisfies the event purchase policy.
     */
    @Transactional(readOnly = true)
    public PurchasePolicyStatus checkPurchasePolicy(String token) {
        validateToken(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        log.info("Purchase policy check requested: sessionId={}, memberId={}", sessionId, memberId);
        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order == null) {
            throw new IllegalArgumentException("No active order found");
        }
        if (order.getItems().isEmpty()) {
            return PurchasePolicyStatus.ok();
        }
        Event event = findEvent(order.getEventId());
        LocalDate buyerDob = getBuyerDateOfBirth(order.getMemberId());
        PurchaseContext ctx = PurchaseContext.forOrder(event, order, order.getMemberId(), buyerDob);
        List<String> violations = event.getEventPurchasePolicy().collectViolations(ctx);
        if (violations.isEmpty()) {
            return PurchasePolicyStatus.ok();
        }
        return PurchasePolicyStatus.violation(String.join("\n", violations));
    }

    public record CheckoutCompletion(UUID purchaseId, BigDecimal chargedAmount) {
    }

    /**
     * Checkout use case (V3-10 / #268): one atomic DB transaction wrapping all the
     * persistent work — order status transition, inventory sell-down and the
     * {@link CompletedPurchase} insert. In {@code jpa} mode the auto-configured
     * transaction manager commits this unit only if the method returns normally.
     *
     * <p>EXTERNAL compensation is unchanged and independent of the DB transaction:
     * {@code processCheckout} charges the payment gateway and issues tickets through
     * the supply gateway, and on a supply failure it already refunds the payment
     * (external side effect) before throwing. When that {@link IllegalStateException}
     * propagates out of this method, Spring rolls back the DB transaction, so the
     * partial DB writes done in the {@code catch} block ({@code saveOrder}/
     * {@code saveEvent}) are discarded and the DB shows no partial sale — while the
     * already-executed external refund/cancel remains in effect. The failure path is
     * verified to actually re-throw (it does), so no explicit
     * {@code setRollbackOnly()} is required.
     */
    @Transactional
    public CheckoutCompletion checkout(String token) {
        return checkout(token, null);
    }

    @Transactional
    public CheckoutCompletion checkout(String token, CardPaymentInfo card) {
        validateToken(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        log.info("Checkout requested: sessionId={}, memberId={}", sessionId, memberId);

        rejectIfMemberSuspended(memberId);

        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order == null) throw new IllegalArgumentException("No active order found");
        validateOrderOwnership(sessionId, order);

        Event event = findEvent(order.getEventId());
        if (event.isCancellationStarted()) {
            throw new IllegalStateException(
                    "This event has been cancelled. Your order cannot be checked out.");
        }
        validateOrderNotExpired(order, event);

        if (order.getItems().isEmpty()) {
            log.warn("Failed to checkout order {}: no items in order", order.getId());
            throw new IllegalStateException("Cannot checkout an empty order");
        }

        if (order.isLotteryWin() && order.isPurchaseWindowExpired(systemClock.now())) {
            order.expire();
            saveOrder(order);
            releaseAllInventoryForOrder(order.getEventId(), order);
            throw new IllegalStateException(
                    "The lottery has ended — buying tickets is no longer available.");
        }

        BuyerContactSnapshot buyerContact = buyerContactFor(memberId);
        LocalDate buyerDob = getBuyerDateOfBirth(memberId);

        CompletedPurchase purchase;
        try {
            purchase = processCheckout(order, event, buyerContact, buyerDob, card);
        } catch (IllegalStateException e) {
            log.warn("Failed to checkout order {}: {}", order.getId(), e.getMessage());
            if (notificationService != null && memberId != null) {
                notificationService.notify(memberId.toString(), "Checkout failed: " + e.getMessage());
            }
            throw e;
        }

        sellAllInventory(event, order);
        saveEvent(event);
        saveOrder(order);
        orderRepository.save(purchase);

        checkAndPublishSoldOut(event);
        log.info("Checkout complete: orderId={}, purchaseId={}, amount={}",
                order.getId(), purchase.purchaseId(), purchase.amount());

        if (notificationService != null && memberId != null) {
            notificationService.notify(memberId.toString(), "Your checkout was completed successfully.");
        }
        if (analyticsCollector != null) {
            analyticsCollector.recordPurchase(order.getTotalTicketCount());
        }
        return new CheckoutCompletion(purchase.purchaseId(), purchase.amount());
    }

    /** Outcome of cancelling an event's orders and refunding its purchases. */
    public record EventCancellationOutcome(
            int activeOrdersCancelled, int purchasesFound,
            int refundsSucceeded, int refundsPending, int refundsFailed) {
        public static EventCancellationOutcome empty() {
            return new EventCancellationOutcome(0, 0, 0, 0, 0);
        }
    }

    /**
     * Cancels every active order for the event (releasing its inventory) and refunds every
     * completed purchase exactly once, using each purchase's original payment transaction id.
     *
     * <p>Idempotent and retry-safe: purchases already marked {@link RefundStatus#REFUNDED} are
     * skipped, and a partial payment-service failure leaves the purchase {@code PENDING}/{@code FAILED}
     * (not refunded) so a later retry processes only the unfinished ones. Notification failures are
     * swallowed so they can never trigger a duplicate refund.
     */
    @Transactional
    public EventCancellationOutcome cancelOrdersAndRefund(UUID eventId, String eventName) {
        int activeCancelled = 0;
        for (ActiveOrder order : orderRepository.findActiveByEventId(eventId)) {
            if (order.isActive()) {
                releaseAllInventoryForOrder(eventId, order);
                order.cancel();
                saveOrder(order);
                activeCancelled++;
                safeNotify(order.getMemberId(),
                        "The event you had tickets reserved for has been cancelled. Your cart has been cleared.");
            }
        }
        log.info("Cancel-event: eventId={}, activeOrdersCancelled={}", eventId, activeCancelled);

        List<CompletedPurchase> purchases = orderRepository.findCompletedByEventId(eventId);
        int succeeded = 0;
        int pending = 0;
        int failed = 0;
        log.info("Cancel-event: eventId={}, completedPurchasesFound={}", eventId, purchases.size());
        for (CompletedPurchase purchase : purchases) {
            if (purchase.isRefunded()) {
                log.warn("Cancel-event: purchase {} already refunded (ref={}), skipping",
                        purchase.purchaseId(), purchase.getRefundReference());
                succeeded++;
                continue;
            }
            String transactionId = purchase.transactionId();
            if (transactionId == null || transactionId.isBlank()) {
                log.warn("Cancel-event: purchase {} has no payment transaction id; cannot refund",
                        purchase.purchaseId());
                purchase.markRefundFailed();
                orderRepository.save(purchase);
                failed++;
                continue;
            }
            RefundResult refund = refundOnce(transactionId, purchase.amount());
            if (refund != null && refund.success()) {
                purchase.markRefunded(refund.refundTransactionId(), purchase.amount());
                orderRepository.save(purchase);
                succeeded++;
                log.info("Cancel-event: refunded purchase {} (ref={})",
                        purchase.purchaseId(), refund.refundTransactionId());
                notifyRefunded(purchase, eventName);
            } else {
                purchase.markRefundPending();
                orderRepository.save(purchase);
                pending++;
                log.warn("Cancel-event: refund still pending for purchase {}: {}",
                        purchase.purchaseId(),
                        refund != null ? refund.errorMessage() : "all gateways failed");
            }
        }
        return new EventCancellationOutcome(activeCancelled, purchases.size(), succeeded, pending, failed);
    }

    /** Attempts a refund across the configured gateways, returning the first success or last failure. */
    private RefundResult refundOnce(String transactionId, BigDecimal amount) {
        RefundResult refund = null;
        for (IPaymentGateway gateway : paymentGateways) {
            try {
                refund = gateway.refund(transactionId, amount.doubleValue());
                if (refund != null && refund.success()) {
                    return refund;
                }
            } catch (Exception e) {
                log.error("Refund gateway failed with exception", e);
            }
        }
        return refund;
    }

    private void notifyRefunded(CompletedPurchase purchase, String eventName) {
        if (purchase.memberId() == null) {
            return; // guest purchase: refunded, but no in-system member notification
        }
        String ref = purchase.getRefundReference();
        String message = "Event " + eventName + " was cancelled. Your purchase of "
                + purchase.amount().toPlainString() + " was refunded."
                + (ref != null && !ref.isBlank() ? " Refund reference: " + ref + "." : "");
        safeNotify(purchase.memberId(), message);
    }

    private void safeNotify(UUID memberId, String message) {
        if (notificationService == null || memberId == null) {
            return;
        }
        try {
            notificationService.notify(memberId.toString(), message);
        } catch (RuntimeException e) {
            // A notification failure must never roll back or repeat a refund.
            log.warn("Cancel-event: notification delivery deferred/failed for member {}", memberId, e);
        }
    }

    public List<PurchaseRecordDTO> getPurchaseHistory(String token) {
        validateToken(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        log.info("Member purchase history requested: memberId={}", memberId);
        if (memberId == null) {
            throw new SecurityException("User must be logged in to view purchase history");
        }
        List<CompletedPurchase> purchases = orderRepository.findCompletedByMemberId(memberId);
        List<PurchaseRecordDTO> result = new ArrayList<>();
        for (CompletedPurchase p : purchases) {
            result.add(PurchaseRecordDTO.from(p));
        }
        log.info("Member purchase history completed: memberId={}, count={}", memberId, result.size());
        return result;
    }

    // ── Virtual Queue methods ──────────────────────────────────────────

    @Transactional
    public UUID createQueue(String token, UUID eventId, int threshold, int flowRate) {
        validateToken(token);
        log.info("Create queue requested: eventId={}, threshold={}, flowRate={}", eventId, threshold, flowRate);

        eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", eventId);
                    return new IllegalArgumentException("Event not found: " + eventId);
                });

        queueRepository.findByEventId(eventId).ifPresent(existing -> {
            log.warn("A virtual queue already exists for this event: eventId={}", eventId);
            throw new IllegalStateException("A virtual queue already exists for this event");
        });

        QueueConfig config = new QueueConfig(threshold, flowRate);
        VirtualQueue queue = new VirtualQueue(UUID.randomUUID(), eventId, config);
        saveQueue(queue);
        log.info("Queue created: queueId={}, eventId={}", queue.getId(), eventId);
        return queue.getId();
    }

    /**
     * Creates a virtual queue using the config-driven default threshold / flow-rate
     * (`ticketing.queue.threshold` / `ticketing.queue.flow-rate`) — V3-13 (#271): the
     * config defines how many users may reserve concurrently before the queue kicks in.
     * The explicit-parameter overload remains for per-event overrides.
     */
    @Transactional
    public UUID createQueue(String token, UUID eventId) {
        return createQueue(token, eventId, defaultQueueThreshold, defaultQueueFlowRate);
    }

    @Transactional
    public QueueEntryDto tryEnterOrQueue(UUID eventId, UUID sessionId) {
        log.info("Try enter or queue: eventId={}, sessionId={}", eventId, sessionId);

        Event event = findEvent(eventId);
        if (event.getStatus() == EventStatus.SOLD_OUT) {
            log.warn("Try enter or queue denied: eventId={}, reason=sold-out", eventId);
            throw new IllegalStateException("Event is sold out — no tickets available.");
        }

        VirtualQueue queue = queueRepository.findByEventId(eventId).orElse(null);
        if (queue == null || !queue.isActive()) {
            return null;
        }

        if (queue.shouldQueue()) {
            QueueEntry entry = queue.enqueue(sessionId, systemClock.now());
            saveQueue(queue);
            log.info("User queued: sessionId={}, eventId={}", sessionId, eventId);
            return entry.toQueueDto();
        } else {
            queue.userEnteredDirectly();
            saveQueue(queue);
            return null;
        }
    }

    @Transactional
    public List<QueueEntryDto> admitNextBatch(String token, UUID eventId) {
        validateToken(token);
        VirtualQueue queue = findQueueByEvent(eventId);
        List<QueueEntry> admitted = queue.admitNextBatch();
        saveQueue(queue);
        log.info("Admitted {} users from queue for eventId={}", admitted.size(), eventId);
        return admitted.stream()
                .map(QueueEntry::toQueueDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void userLeft(UUID eventId) {
        userLeft(eventId, null);
    }

    /**
     * Releases the given session's place in the event queue. When {@code sessionId}
     * is provided the session's queue entry is cleared (so a previously-admitted
     * session no longer bypasses the queue on re-entry); a {@code null} session id
     * falls back to a plain active-slot decrement for legacy callers. Either way,
     * any freed slot auto-admits the next waiting batch.
     */
    @Transactional
    public void userLeft(UUID eventId, UUID sessionId) {
        log.info("User left queue: eventId={}, sessionId={}", eventId, sessionId);
        VirtualQueue queue = queueRepository.findByEventId(eventId).orElse(null);
        if (queue != null) {
            if (sessionId != null) {
                queue.leave(sessionId);
            } else {
                queue.userLeft();
            }
            if (queue.getWaitingCount() > 0 && !queue.shouldQueue()) {
                List<QueueEntry> admitted = queue.admitNextBatch();
                log.info("Auto-admitted {} users from queue for eventId={} after user left",
                        admitted.size(), eventId);
            }
            saveQueue(queue);
        }
    }

    @Transactional
    public void updateQueueConfig(String token, UUID eventId, int threshold, int flowRate) {
        validateToken(token);
        VirtualQueue queue = findQueueByEvent(eventId);
        queue.updateConfig(new QueueConfig(threshold, flowRate));
        saveQueue(queue);
        log.info("Queue config updated: eventId={}", eventId);
    }

    @Transactional
    public void flushQueue(String token, UUID eventId) {
        validateToken(token);
        VirtualQueue queue = findQueueByEvent(eventId);
        queue.flush();
        saveQueue(queue);
        log.info("Queue flushed: eventId={}", eventId);
    }

    @Transactional
    public void deleteQueue(String token, UUID eventId) {
        validateToken(token);
        VirtualQueue queue = findQueueByEvent(eventId);
        queueRepository.delete(queue.getId());
        log.info("Queue deleted: queueId={}, eventId={}", queue.getId(), eventId);
    }

    public List<VirtualQueueDto> getAllActiveQueues(String token) {
        validateToken(token);
        log.info("Getting all active queues");
        return queueRepository.findAllActive().stream()
                .map(VirtualQueue::toVirtualQueueDto)
                .collect(Collectors.toList());
    }

    public List<VirtualQueueDto> getAllQueues(String token) {
        validateToken(token);
        log.info("Getting all event queues");
        return queueRepository.findAll().stream()
                .map(VirtualQueue::toVirtualQueueDto)
                .collect(Collectors.toList());
    }

    public VirtualQueueDto getQueueForEvent(UUID eventId) {
        log.info("Queue lookup requested: eventId={}", eventId);
        return findQueueByEvent(eventId).toVirtualQueueDto();
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 10_000)
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void expireOrders() {
        if (!isPersistenceDatabaseReady()) {
            return;
        }
        runScheduledMaintenance(() -> {
            log.info("Order expiry job started");
            if (orderTimeDomainService != null) {
                orderTimeDomainService.expireOrders();
            }
        });
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60_000)
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void retryPendingRefunds() {
        if (!isPersistenceDatabaseReady()) {
            return;
        }
        runScheduledMaintenance(this::retryPendingRefundsInTransaction);
    }

    private void retryPendingRefundsInTransaction() {
        List<com.ticketing.domain.order.FailedCheckoutRefund> pendingRefunds = orderRepository.findPendingRefunds();
        for (com.ticketing.domain.order.FailedCheckoutRefund pending : pendingRefunds) {
            log.info("Retrying pending refund for transaction {}", pending.getTransactionId());
            boolean success = false;
            for (IPaymentGateway gateway : paymentGateways) {
                try {
                    RefundResult result = gateway.refund(pending.getTransactionId(), pending.getAmount().doubleValue());
                    if (result != null && result.success()) {
                        success = true;
                        break;
                    }
                } catch (Exception e) {
                    log.error("Retry Refund Gateway failed with exception", e);
                }
            }
            if (success) {
                pending.markRefunded();
                orderRepository.save(pending);
                log.info("Successfully retried pending refund for transaction {}", pending.getTransactionId());
                safeNotify(pending.getMemberId(), 
                        "A previous checkout failure has been resolved and your payment of " 
                        + pending.getAmount().toPlainString() + " has been refunded successfully.");
            } else {
                log.warn("Retry failed for pending refund transaction {}", pending.getTransactionId());
            }
        }
    }

    // ── Reservation internals ───────────────────────────────────────

    @Transactional
    public ActiveOrder findOrCreateActiveOrder(UUID sessionId, UUID memberId, UUID eventId) {
        rejectIfMemberSuspended(memberId);
        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order != null) {
            if (!order.getEventId().equals(eventId)) {
                throw new IllegalStateException("You already have an active order for another event. Please checkout or clear your cart first.");
            }
            return order;
        }

        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required to create a new order");
        }
        Event event = findEvent(eventId);
        if (!event.isPublished()) {
            throw new IllegalStateException("Event is not available for purchase");
        }
        rejectIfCompanyInactive(event);

        if (event.isLottery()) {
            return findOrCreateLotteryOrder(sessionId, memberId, eventId);
        }

        order = new ActiveOrder(UUID.randomUUID(), sessionId, memberId, eventId, systemClock.now());
        saveOrder(order);
        log.info("Order created: orderId={}, sessionId={}, eventId={}", order.getId(), sessionId, eventId);
        return order;
    }

    private ActiveOrder findOrCreateLotteryOrder(UUID sessionId, UUID memberId, UUID eventId) {
        if (memberId == null) {
            throw new IllegalStateException(
                    "Tickets for this event are allocated by lottery. Log in and check your lottery status.");
        }
        // Check if this member won
        java.util.Optional<ActiveOrder> winOrder =
                orderRepository.findActiveLotteryWinByMemberIdAndEventId(memberId, eventId);
        if (winOrder.isPresent()) {
            ActiveOrder wo = winOrder.get();
            if (wo.isPurchaseWindowExpired(systemClock.now())) {
                wo.expire();
                saveOrder(wo);
                releaseAllInventoryForOrder(eventId, wo);
                throw new IllegalStateException(
                        "The lottery has ended — buying tickets is no longer available.");
            }
            return wo;
        }
        // Not a winner — inspect the draw state for this event
        List<ActiveOrder> allWinOrders = orderRepository.findActiveByEventId(eventId).stream()
                .filter(com.ticketing.domain.order.ActiveOrder::isLotteryWin)
                .toList();
        if (allWinOrders.isEmpty()) {
            // Draw has not been run yet — no one may buy
            throw new IllegalStateException(
                    "Tickets for this event are allocated by lottery. Register for the lottery instead.");
        }
        boolean allExpired = allWinOrders.stream()
                .allMatch(o -> o.isPurchaseWindowExpired(systemClock.now()));
        if (!allExpired) {
            // Winner window still active — only winners may buy
            throw new IllegalStateException(
                    "Tickets for this event are allocated by lottery. Register for the lottery instead.");
        }
        // All winner windows have expired — event is now open to regular buyers
        log.info("Lottery winner window closed for eventId={}, allowing regular order", eventId);
        ActiveOrder order = new ActiveOrder(UUID.randomUUID(), sessionId, memberId, eventId, systemClock.now());
        saveOrder(order);
        return order;
    }

    private void releaseAllInventoryForOrder(UUID eventId, ActiveOrder order) {
        try {
            Event event = findEvent(eventId);
            releaseAllInventory(event, order);
        } catch (Exception ex) {
            log.warn("Could not release inventory for expired lottery order {}: {}", order.getId(), ex.getMessage());
        }
    }

    @Transactional
    public ActiveOrder getActiveOrder(UUID sessionId, UUID memberId) {
        ActiveOrder order = null;
        if (memberId != null) {
            order = orderRepository.findActiveByMemberId(memberId).orElse(null);
            if (order != null && !order.getSessionId().equals(sessionId)) {
                order.updateSessionId(sessionId);
                saveOrder(order);
            }
        }
        if (order == null) {
            order = orderRepository.findActiveBySessionId(sessionId).orElse(null);
            if (order != null && memberId != null && order.getMemberId() == null) {
                order.updateMemberId(memberId);
                saveOrder(order);
            }
        }
        if (order != null) {
            Event event = findEvent(order.getEventId());
            if (order.isExpiredAt(systemClock.now(), event.getLockTimerDuration().getDuration())) {
                releaseAllInventory(event, order);
                order.expire();
                checkAndPublishAvailable(event);
                saveEvent(event);
                saveOrder(order);
                return null;
            }
        }
        return order;
    }

    @Transactional
    public ActiveOrder getValidatedActiveOrder(UUID sessionId, UUID memberId) {
        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order == null) throw new IllegalArgumentException("No active order found");
        validateOrderOwnership(sessionId, order);
        return order;
    }

    @Transactional
    public List<UUID> addSelectionToOrder(UUID sessionId, ActiveOrder order, SelectionRequest request) {
        rejectIfMemberSuspended(order.getMemberId());
        validateOrderOwnership(sessionId, order);
        if (!order.getEventId().equals(request.eventId())) {
            log.warn("Failed to add selection to order: selection event {} does not match order event {}", request.eventId(), order.getEventId());
            throw new IllegalArgumentException("Selection event does not match order event");
        }

        try {
            Event event = findEvent(order.getEventId());
            validateOrderNotExpired(order, event);
            List<String> selectionErrors = collectSelectionErrors(event, order, request);
            if (!selectionErrors.isEmpty()) {
                throw new IllegalStateException(String.join("\n", selectionErrors));
            }

            List<UUID> itemIds = new ArrayList<>();
            for (SelectionRequest.SeatPick pick : request.seats()) {
                itemIds.add(lockSeat(order, event, pick.zoneId(), pick.seatId()));
            }
            for (SelectionRequest.GAPick pick : request.gaQuantities()) {
                itemIds.add(lockGA(order, event, pick.zoneId(), pick.quantity()));
            }

            saveEvent(event);
            saveOrder(order);
            // No sold-out check here: reserving tickets into a cart does not sell them, and the
            // event must stay PUBLISHED (and inventory-editable) while tickets are merely locked.
            // Sold-out is decided at checkout, once the tickets are actually sold.
            if (analyticsCollector != null) {
                analyticsCollector.recordReservation(request.additionalTicketCount());
            }
            return itemIds;
        } catch (RuntimeException ex) {
            discardEmptyActiveOrder(order);
            throw ex;
        }
    }

    // ── Inventory lock/release ──────────────────────────────────────

    private UUID lockSeat(ActiveOrder order, Event event, UUID zoneId, UUID seatId) {
        InventoryZone zone = event.findZone(zoneId);
        zone.lockSeat(seatId);
        OrderItem item = OrderItem.forSeat(UUID.randomUUID(), zoneId, seatId, zone.getPricePerTicket());
        order.addItem(item);
        log.info("Seat added to order: orderId={}, seatId={}", order.getId(), seatId);
        return item.getId();
    }

    private UUID lockGA(ActiveOrder order, Event event, UUID zoneId, int quantity) {
        InventoryZone zone = event.findZone(zoneId);
        OrderItem item = order.findItemByZoneId(zoneId).orElse(null);
        if (item == null) {
            zone.lockGA(quantity);
            item = OrderItem.forGA(UUID.randomUUID(), zoneId, quantity, zone.getPricePerTicket());
            order.addItem(item);
        } else {
            zone.lockGA(quantity);
            item.updateQuantity(item.getQuantity() + quantity);
        }
        log.info("GA tickets added: orderId={}, zoneId={}, quantity={}", order.getId(), zoneId, quantity);
        return item.getId();
    }

    private void updateGAQuantityInternal(ActiveOrder order, Event event, UUID zoneId, int newQuantity) {
        OrderItem item = order.findItemByZoneId(zoneId)
                .orElseThrow(() -> new IllegalArgumentException("No GA item found for zone: " + zoneId));

        InventoryZone zone = event.findZone(zoneId);
        int oldQuantity = item.getQuantity();
        int diff = newQuantity - oldQuantity;

        if (diff > 0) {
            zone.lockGA(diff);
        } else if (diff < 0) {
            zone.releaseGA(-diff);
        }

        item.updateQuantity(newQuantity);
    }

    private void releaseInventoryForItem(Event event, OrderItem item) {
        event.releaseReservationFor(item);
    }

    private void releaseAllInventory(Event event, ActiveOrder order) {
        for (OrderItem item : order.getItems()) {
            try {
                event.releaseReservationFor(item);
            } catch (Exception e) {
                log.error("Failed to release inventory for item: {}", item.getId(), e);
            }
        }
    }

    private void sellAllInventory(Event event, ActiveOrder order) {
        for (OrderItem item : order.getItems()) {
            InventoryZone zone = event.findZone(item.getZoneId());
            if (item.isAssignedSeat()) {
                zone.sellSeat(item.getSeatId());
            } else {
                zone.sellGA(item.getQuantity());
            }
        }
    }

    private void checkAndPublishSoldOut(Event event) {
        // Only when every ticket is actually SOLD — not merely reserved in carts, which can
        // still be released. This runs at checkout (after the sale), never at reservation.
        if (event.isFullySold() && event.isPublished()) {
            event.markSoldOut();
            saveEvent(event);
            if (notificationService != null && memberRepository != null) {
                List<Member> staff = memberRepository.findByCompanyAppointment(event.getCompanyName());
                for (Member member : staff) {
                    if (member.hasStaffAppointment(event.getCompanyName(), com.ticketing.domain.member.StaffAppointment.StaffRole.OWNER) ||
                        member.hasStaffAppointment(event.getCompanyName(), com.ticketing.domain.member.StaffAppointment.StaffRole.MANAGER)) {
                        notificationService.notify(member.getId().toString(), "Event sold out: " + event.getName());
                    }
                }
            }
        }
    }

    /**
     * Checks if a sold-out event has available tickets (e.g., after an order cancellation).
     * If so, transitions the event status back to PUBLISHED and persists the change.
     */
    private void checkAndPublishAvailable(Event event) {
        if (event.reopenAvailabilityIfTicketsFreed()) {
            saveEvent(event);
        }
    }

    // ── Checkout internals ──────────────────────────────────────────

    private CompletedPurchase processCheckout(ActiveOrder order, Event event,
                                               BuyerContactSnapshot buyerContact,
                                               LocalDate buyerDateOfBirth, CardPaymentInfo card) {
        // Re-check suspension as late as possible: a member could have been suspended
        // after checkout began but before any payment/issuance side effect runs.
        rejectIfMemberSuspended(order.getMemberId());
        requirePurchasePolicyCompliance(event, PurchaseContext.forOrder(
                event, order, order.getMemberId(), buyerDateOfBirth));

        BigDecimal finalAmount = event.calculateOrderTotal(order, systemClock.now());

        order.startCheckout();

        PaymentResult payment = chargePayment(order, event, buyerContact, finalAmount, card);
        if (payment == null || !payment.success()) {
            order.revertToActive();
            throw new IllegalStateException("Payment failed: " + (payment != null ? payment.errorMessage() : "All gateways failed"));
        }

        SupplyResult supply = supplyTickets(order, event, buyerContact);
        if (supply == null || !supply.success()) {
            boolean refundSuccess = refundPayment(payment.transactionId(), finalAmount, event.getId(), order.getMemberId());
            order.revertToActive();
            if (refundSuccess) {
                throw new IllegalStateException("Ticket generation failed. Payment has been refunded: "
                        + (supply != null ? supply.errorMessage() : "All gateways failed"));
            } else {
                throw new IllegalStateException("Ticket generation failed. Payment refund is pending: "
                        + (supply != null ? supply.errorMessage() : "All gateways failed"));
            }
        }

        order.complete();

        return new CompletedPurchase(
                UUID.randomUUID(),
                event.getId(),
                event.getName(),
                event.getCompanyName(),
                order.getMemberId(),
                buyerContact.getUsername(),
                payment.transactionId(),
                finalAmount,
                systemClock.now());
    }

    public boolean refundPayment(String transactionId, BigDecimal amount, UUID eventId, UUID memberId) {
        RefundResult refund = null;
        for (IPaymentGateway gateway : paymentGateways) {
            try {
                refund = gateway.refund(transactionId, amount.doubleValue());
                if (refund != null && refund.success()) {
                    return true;
                }
            } catch (Exception e) {
                log.error("Refund Gateway failed with exception", e);
            }
        }

        log.error("ESCALATION: Refund failed after ticket supply failure: reason={}",
                refund != null ? refund.errorMessage() : "All gateways failed");
        
        com.ticketing.domain.order.FailedCheckoutRefund pendingRefund = new com.ticketing.domain.order.FailedCheckoutRefund(
                UUID.randomUUID(), eventId, memberId, transactionId, amount, systemClock.now());
        orderRepository.save(pendingRefund);
        
        safeNotify(memberId, 
                "Ticket generation failed but we could not immediately refund your payment. "
                + "A pending refund has been registered and will be processed shortly.");
        
        return false;
    }

    private void requirePurchasePolicyCompliance(Event event, PurchaseContext ctx) {
        List<String> violations = event.getEventPurchasePolicy().collectViolations(ctx);
        if (!violations.isEmpty()) {
            throw new IllegalStateException(String.join("\n", violations));
        }
    }

    private PaymentResult chargePayment(ActiveOrder order, Event event, BuyerContactSnapshot buyerContact,
                                        BigDecimal finalAmount, CardPaymentInfo card) {
        PaymentDetails details = card == null
                ? new PaymentDetails(order.getId(), event.getId(), order.getMemberId(), buyerContact.getEmail())
                : new PaymentDetails(order.getId(), event.getId(), order.getMemberId(), buyerContact.getEmail(),
                        card.currency(), card.cardNumber(), card.month(), card.year(),
                        card.holder(), card.cvv(), card.cardId());
        PaymentResult payment = null;
        for (IPaymentGateway gateway : paymentGateways) {
            try {
                payment = gateway.charge(finalAmount, details);
                if (payment != null && payment.success()) {
                    break;
                }
            } catch (Exception e) {
                log.error("Payment Gateway failed with exception", e);
            }
        }
        return payment;
    }

    private SupplyResult supplyTickets(ActiveOrder order, Event event, BuyerContactSnapshot buyerContact) {
        SupplyResult supply = null;
        for (ITicketSupplyGateway gateway : ticketSupplyGateways) {
            try {
                supply = gateway.issueTickets(ticketRequests(order, event),
                        new CustomerInfo(order.getMemberId() == null ? null : order.getMemberId().toString(),
                                buyerContact.getEmail(), buyerContact.getUsername()));
                if (supply != null && supply.success()) {
                    break;
                } else if (supply != null && supply.issuedTicketCodes() != null && !supply.issuedTicketCodes().isEmpty()) {
                    gateway.cancelTickets(supply.issuedTicketCodes());
                }
            } catch (Exception e) {
                log.error("Gateway failed with exception", e);
            }
        }
        return supply;
    }

    private List<TicketRequest> ticketRequests(ActiveOrder order, Event event) {
        List<TicketRequest> tickets = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            if (item.isAssignedSeat()) {
                com.ticketing.domain.event.Seat seat = null;
                try {
                    seat = event.findZone(item.getZoneId()).getSeats().stream()
                            .filter(s -> s.getId().equals(item.getSeatId())).findFirst().orElse(null);
                } catch (Exception e) {}
                String row = seat != null ? seat.getRow() : "";
                String number = seat != null ? seat.getSeatNumber() : "";
                
                tickets.add(new TicketRequest(event.getId().toString(), item.getZoneId().toString(),
                        item.getId().toString(), item.getSeatId().toString(), row, number));
            } else {
                for (int i = 0; i < item.getQuantity(); i++) {
                    tickets.add(new TicketRequest(event.getId().toString(), item.getZoneId().toString(),
                            item.getId() + "-" + (i + 1), null, null, null));
                }
            }
        }
        return tickets;
    }

    private BuyerContactSnapshot buyerContactFor(UUID memberId) {
        if (memberId == null || memberRepository == null) {
            return BuyerContactSnapshot.empty();
        }
        return memberRepository.findById(memberId)
                .map(member -> new BuyerContactSnapshot(
                        member.getEmail(),
                        member.getUsername(),
                        member.getPhoneNumber()))
                .orElseGet(BuyerContactSnapshot::empty);
    }

    private void rejectIfMemberSuspended(UUID memberId) {
        if (memberId == null || memberRepository == null) {
            return;
        }
        memberRepository.findById(memberId)
                .ifPresent(member -> member.rejectIfSuspended(systemClock.now()));
    }

    private LocalDate getBuyerDateOfBirth(UUID memberId) {
        if (memberId == null || memberRepository == null) {
            return null;
        }
        return memberRepository.findById(memberId).map(Member::getDateOfBirth).orElse(null);
    }

    // ── Selection validation ────────────────────────────────────────

    private List<String> collectSelectionErrors(
            Event event,
            ActiveOrder order,
            SelectionRequest request
    ) {
        List<String> errors = new ArrayList<>();
        if (request == null) {
            errors.add("request is required");
            return errors;
        }
        if (request.isEmpty()) {
            errors.add("selection must include at least one seat or quantity");
            return errors;
        }
        if (event.getStatus() != EventStatus.PUBLISHED) {
            errors.add("Event is not selectable in status: " + event.getStatus());
            return errors;
        }

        for (SelectionRequest.SeatPick pick : request.seats()) {
            try {
                validateSeatPick(event, pick);
            } catch (RuntimeException ex) {
                if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
                    errors.add(ex.getMessage());
                }
            }
        }

        Map<UUID, Integer> totalsByZone = new HashMap<>();
        for (SelectionRequest.GAPick pick : request.gaQuantities()) {
            totalsByZone.merge(pick.zoneId(), pick.quantity(), Integer::sum);
        }
        for (var entry : totalsByZone.entrySet()) {
            try {
                validateGAPick(event, entry.getKey(), entry.getValue());
            } catch (RuntimeException ex) {
                if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
                    errors.add(ex.getMessage());
                }
            }
        }

        errors.addAll(event.getEventPurchasePolicy().collectViolations(PurchaseContext.forReservation(
                event, order, order.getMemberId(), getBuyerDateOfBirth(order.getMemberId()), request)));
        return errors.stream().distinct().toList();
    }

    private void validateSeatPick(Event event, SelectionRequest.SeatPick pick) {
        InventoryZone zone = findZoneInEvent(event, pick.zoneId());
        if (!zone.isAssigned()) {
            throw new IllegalArgumentException(
                    "Zone " + zone.getName() + " is GA — use a quantity, not a seat id");
        }
        Seat seat;
        try {
            seat = zone.findSeat(pick.seatId());
        } catch (IllegalArgumentException notFound) {
            throw new IllegalArgumentException(
                    "Seat " + pick.seatId() + " not found in zone " + zone.getName());
        }
        if (!seat.isAvailable()) {
            throw new IllegalStateException(
                    "Seat " + seat.getRow() + "-" + seat.getSeatNumber()
                    + " is not available (status=" + seat.getStatus() + ")");
        }
    }

    private void validateGAPick(Event event, UUID zoneId, int requested) {
        InventoryZone zone = findZoneInEvent(event, zoneId);
        if (!zone.isGA()) {
            throw new IllegalArgumentException(
                    "Zone " + zone.getName() + " is assigned-seating — pick specific seats, not a quantity");
        }
        if (zone.getAvailableCount() < requested) {
            throw new IllegalStateException(
                    "Not enough tickets in zone " + zone.getName()
                    + " (requested " + requested + ", available " + zone.getAvailableCount() + ")");
        }
    }

    private static InventoryZone findZoneInEvent(Event event, UUID zoneId) {
        try {
            return event.findZone(zoneId);
        } catch (IllegalArgumentException notFound) {
            throw new IllegalArgumentException(
                    "Zone " + zoneId + " is not part of event " + event.getId());
        }
    }

    // ── Persistence helpers ─────────────────────────────────────────

    private Event findEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", eventId);
                    return new IllegalArgumentException("Event not found: " + eventId);
                });
    }

    private void rejectIfCompanyInactive(Event event) {
        if (companyRepository == null) {
            return;
        }
        Company company = companyRepository.findByName(event.getCompanyName())
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + event.getCompanyName()));
        if (!company.isActive()) {
            throw new IllegalStateException("Company is suspended or closed: " + company.getName());
        }
    }

    private void saveEvent(Event event) {
        try {
            eventRepository.save(event);
        } catch (OptimisticLockException ex) {
            log.warn("Event save conflict: eventId={}", event.getId());
            throw new IllegalStateException("Event inventory changed concurrently. Please retry.", ex);
        }
    }

    private void saveOrder(ActiveOrder order) {
        try {
            orderRepository.save(order);
        } catch (OptimisticLockException ex) {
            log.warn("Order save conflict: orderId={}", order.getId());
            throw new IllegalStateException("Order changed concurrently. Please retry.", ex);
        }
    }

    private void saveQueue(VirtualQueue queue) {
        try {
            queueRepository.save(queue);
        } catch (OptimisticLockException ex) {
            log.warn("Queue save conflict: queueId={}", queue.getId());
            throw ex;
        }
    }

    private VirtualQueue findQueueByEvent(UUID eventId) {
        return queueRepository.findByEventId(eventId)
                .orElseThrow(() -> new IllegalStateException("No virtual queue for event: " + eventId));
    }

    private void validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token is required to access an order");
        }
        if (!sessionTokenService.isValid(token)) {
            throw new IllegalArgumentException("Authentication token is invalid");
        }
    }

    private void validateOrderOwnership(UUID sessionId, ActiveOrder order) {
        if (!order.getSessionId().equals(sessionId)) {
            throw new IllegalStateException("Order does not belong to this session");
        }
    }

    private void validateOrderNotExpired(ActiveOrder order, Event event) {
        if (order.isExpiredAt(systemClock.now(), event.getLockTimerDuration().getDuration())) {
            log.warn("Order has expired: orderId={}, eventId={}", order.getId(), event.getId());
            throw new IllegalStateException("Order has expired");
        }
    }

    private void discardEmptyActiveOrder(ActiveOrder order) {
        if (!order.isActive() || !order.getItems().isEmpty()) {
            return;
        }
        order.cancel();
        saveOrder(order);
        log.info("Discarded empty active order after failed add: orderId={}", order.getId());
    }
}
