package com.ticketing.application.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.application.ISystemClock;
import com.ticketing.application.SelectionRequest;
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
import com.ticketing.domain.event.PolicyResult;
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
import com.ticketing.domain.queue.IQueueRepository;
import com.ticketing.domain.queue.QueueConfig;
import com.ticketing.domain.queue.QueueEntry;
import com.ticketing.domain.queue.VirtualQueue;
import com.ticketing.domain.services.OrderTimeDomainService;

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

    // V3-13 (#271): per-event virtual-queue defaults are config-driven (ticketing.queue.*).
    // The field initializers are the fallback for `new`-built unit tests, where Spring does
    // not perform @Value injection; in the running app @Value overrides them from config/env.
    @org.springframework.beans.factory.annotation.Value("${ticketing.queue.threshold:100}")
    private int defaultQueueThreshold = 100;
    @org.springframework.beans.factory.annotation.Value("${ticketing.queue.flow-rate:10}")
    private int defaultQueueFlowRate = 10;

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
                ticketSupplyGateways, systemClock, queueRepository, orderTimeDomainService, notificationService);
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
            @org.springframework.beans.factory.annotation.Autowired(required = false) INotificationService notificationService) {
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
    }

    // ── Order creation & ticket reservation ─────────────────────────

    @Transactional
    public UUID createOrder(String token, UUID eventId) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        rejectIfMemberSuspended(memberId);
        return findOrCreateActiveOrder(sessionId, memberId, eventId).getId();
    }

    @Transactional
    public UUID addSeatToOrder(String token, UUID eventId, UUID zoneId, UUID seatId) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        rejectIfMemberSuspended(memberId);
        ActiveOrder order = findOrCreateActiveOrder(sessionId, memberId, eventId);
        validateOrderOwnership(sessionId, order);
        return addSelectionToOrder(sessionId, order,
                new com.ticketing.domain.order.SelectionRequest(order.getEventId(),
                        List.of(new com.ticketing.domain.order.SelectionRequest.SeatPick(zoneId, seatId)),
                        List.of())).get(0);
    }

    @Transactional
    public UUID addGATicketsToOrder(String token, UUID eventId, UUID zoneId, int quantity) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        rejectIfMemberSuspended(memberId);
        ActiveOrder order = findOrCreateActiveOrder(sessionId, memberId, eventId);
        validateOrderOwnership(sessionId, order);
        return addSelectionToOrder(sessionId, order,
                new com.ticketing.domain.order.SelectionRequest(order.getEventId(),
                        List.of(),
                        List.of(new com.ticketing.domain.order.SelectionRequest.GAPick(zoneId, quantity)))).get(0);
    }

    @Transactional
    public List<UUID> addSelectionToOrder(String token, SelectionRequest request) {
        validateToken(token);
        if (request == null)
            throw new IllegalArgumentException("request is required");
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        rejectIfMemberSuspended(memberId);
        ActiveOrder order = findOrCreateActiveOrder(sessionId, memberId, request.eventId());
        com.ticketing.domain.order.SelectionRequest domainRequest = new com.ticketing.domain.order.SelectionRequest(
                request.eventId(),
                request.seats().stream()
                        .map(s -> new com.ticketing.domain.order.SelectionRequest.SeatPick(s.zoneId(), s.seatId()))
                        .toList(),
                request.gaQuantities().stream()
                        .map(g -> new com.ticketing.domain.order.SelectionRequest.GAPick(g.zoneId(), g.quantity()))
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

        releaseInventoryForItem(event, item);

        if (item.isAssignedSeat()) {
            PurchaseContext ctx = new PurchaseContext(order, memberId, null, event, java.util.Set.of());
            PolicyResult policyResult = event.getEventPurchasePolicy().isAllowed(ctx);
            if (!policyResult.allowed()) {
                event.findZone(item.getZoneId()).lockSeat(item.getSeatId());
                throw new IllegalStateException(policyResult.reason());
            }
        }

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
        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order == null)
            return null;
        return new ActiveOrderDto(
                order.getId(),
                order.getSessionId(),
                order.getMemberId(),
                order.getEventId(),
                order.getCreatedAt(),
                order.getStatus().name(),
                order.getItemsDto(),
                order.getTotalPrice());
    }

    // ── Checkout ────────────────────────────────────────────────────

    /**
     * Estimates the amount that would be charged at checkout for the active order,
     * applying the event discount policy and optional coupon without side effects.
     */
    @Transactional
    public CheckoutQuote quoteCheckout(String token, String couponCode) {
        validateToken(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order == null) {
            throw new IllegalArgumentException("No active order found");
        }
        if (order.getItems().isEmpty()) {
            throw new IllegalArgumentException("No active order with tickets to checkout");
        }
        Event event = findEvent(order.getEventId());
        BigDecimal subtotal = order.getTotalPrice();
        BigDecimal total = discountedCheckoutAmount(order, event, normalizeCoupon(couponCode));
        return new CheckoutQuote(subtotal, total);
    }

    public record CheckoutQuote(BigDecimal subtotal, BigDecimal total) {
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
    public CheckoutCompletion checkout(String token, String couponCode) {
        validateToken(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);

        rejectIfMemberSuspended(memberId);

        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order == null) throw new IllegalArgumentException("No active order found");
        validateOrderOwnership(sessionId, order);

        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);

        if (order.getItems().isEmpty()) {
            log.warn("Failed to checkout order {}: no items in order", order.getId());
            throw new IllegalStateException("Cannot checkout an empty order");
        }

        BuyerContactSnapshot buyerContact = buyerContactFor(memberId);
        LocalDate buyerDob = getBuyerDateOfBirth(memberId);

        CompletedPurchase purchase;
        try {
            purchase = processCheckout(order, event, buyerContact, normalizeCoupon(couponCode), buyerDob);
        } catch (IllegalStateException e) {
            saveOrder(order);
            if (e.getMessage() != null && e.getMessage().contains("Ticket generation failed")) {
                releaseAllInventory(event, order);
                checkAndPublishAvailable(event);
                saveEvent(event);
            }
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
        return new CheckoutCompletion(purchase.purchaseId(), purchase.amount());
    }

    @Transactional
    public List<CompletedPurchase> refundEventPurchases(UUID eventId) {
        List<CompletedPurchase> purchases = orderRepository.findCompletedByEventId(eventId);
        for (CompletedPurchase purchase : purchases) {
            refundPayment(purchase.transactionId(), purchase.amount());
        }
        if (notificationService != null) {
            for (CompletedPurchase purchase : purchases) {
                if (purchase.memberId() != null) {
                    notificationService.notify(purchase.memberId().toString(),
                            "The event you purchased tickets for has been cancelled and you have been refunded.");
                }
            }
        }
        return purchases;
    }

    public List<PurchaseRecordDTO> getPurchaseHistory(String token) {
        validateToken(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        if (memberId == null) {
            throw new SecurityException("User must be logged in to view purchase history");
        }
        List<CompletedPurchase> purchases = orderRepository.findCompletedByMemberId(memberId);
        List<PurchaseRecordDTO> result = new ArrayList<>();
        for (CompletedPurchase p : purchases) {
            result.add(PurchaseRecordDTO.from(p));
        }
        return result;
    }

    // ── Virtual Queue methods ──────────────────────────────────────────

    @Transactional
    public UUID createQueue(String token, UUID eventId, int threshold, int flowRate) {
        validateToken(token);

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
        VirtualQueue queue = queueRepository.findByEventId(eventId).orElse(null);
        if (queue != null) {
            queue.userLeft();
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

    public List<VirtualQueueDto> getAllActiveQueues(String token) {
        validateToken(token);
        log.info("Getting all active queues");
        return queueRepository.findAllActive().stream()
                .map(VirtualQueue::toVirtualQueueDto)
                .collect(Collectors.toList());
    }

    public VirtualQueueDto getQueueForEvent(UUID eventId) {
        return findQueueByEvent(eventId).toVirtualQueueDto();
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 10_000)
    @Transactional
    public void expireOrders() {
        if (orderTimeDomainService != null) {
            orderTimeDomainService.expireOrders();
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

        order = new ActiveOrder(UUID.randomUUID(), sessionId, memberId, eventId, systemClock.now());
        saveOrder(order);
        log.info("Order created: orderId={}, sessionId={}, eventId={}", order.getId(), sessionId, eventId);
        return order;
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
    public List<UUID> addSelectionToOrder(UUID sessionId, ActiveOrder order, com.ticketing.domain.order.SelectionRequest request) {
        rejectIfMemberSuspended(order.getMemberId());
        validateOrderOwnership(sessionId, order);
        if (!order.getEventId().equals(request.eventId())) {
            log.warn("Failed to add selection to order: selection event {} does not match order event {}", request.eventId(), order.getEventId());
            throw new IllegalArgumentException("Selection event does not match order event");
        }

        try {
            Event event = findEvent(order.getEventId());
            validateOrderNotExpired(order, event);
            validateSelection(request, event);
            validatePurchasePolicyOnReservation(event, order, request);

            List<UUID> itemIds = new ArrayList<>();
            for (com.ticketing.domain.order.SelectionRequest.SeatPick pick : request.seats()) {
                itemIds.add(lockSeat(order, event, pick.zoneId(), pick.seatId()));
            }
            for (com.ticketing.domain.order.SelectionRequest.GAPick pick : request.gaQuantities()) {
                itemIds.add(lockGA(order, event, pick.zoneId(), pick.quantity()));
            }

            saveEvent(event);
            saveOrder(order);
            checkAndPublishSoldOut(event);
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
        InventoryZone zone = event.findZone(item.getZoneId());
        if (item.isAssignedSeat()) {
            zone.releaseSeat(item.getSeatId());
        } else {
            zone.releaseGA(item.getQuantity());
        }
    }

    private void releaseAllInventory(Event event, ActiveOrder order) {
        for (OrderItem item : order.getItems()) {
            try {
                releaseInventoryForItem(event, item);
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
        if (!event.hasAvailableTickets() && event.isPublished()) {
            event.markSoldOut();
            saveEvent(event);
        }
    }

    /**
     * Checks if a sold-out event has available tickets (e.g., after an order cancellation).
     * If so, transitions the event status back to PUBLISHED and persists the change.
     */
    private void checkAndPublishAvailable(Event event) {
        if (event.hasAvailableTickets() && event.getStatus() == EventStatus.SOLD_OUT) {
            event.markAvailable();
            saveEvent(event);
        }
    }

    // ── Checkout internals ──────────────────────────────────────────

    private CompletedPurchase processCheckout(ActiveOrder order, Event event,
                                               BuyerContactSnapshot buyerContact, String couponCode,
                                               LocalDate buyerDateOfBirth) {
        // Re-check suspension as late as possible: a member could have been suspended
        // after checkout began but before any payment/issuance side effect runs.
        rejectIfMemberSuspended(order.getMemberId());
        validatePurchasePolicy(event, order, order.getMemberId(), buyerDateOfBirth);

        BigDecimal finalAmount = discountedCheckoutAmount(order, event, couponCode);

        order.startCheckout();

        PaymentResult payment = chargePayment(order, event, buyerContact, finalAmount);
        if (payment == null || !payment.success()) {
            order.revertToActive();
            throw new IllegalStateException("Payment failed: " + (payment != null ? payment.errorMessage() : "All gateways failed"));
        }

        SupplyResult supply = supplyTickets(order, event, buyerContact);
        if (supply == null || !supply.success()) {
            refundPayment(payment.transactionId(), finalAmount);
            order.cancel();
            throw new IllegalStateException("Ticket generation failed. Payment has been refunded: "
                    + (supply != null ? supply.errorMessage() : "All gateways failed"));
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

    public void refundPayment(String transactionId, BigDecimal amount) {
        RefundResult refund = null;
        for (IPaymentGateway gateway : paymentGateways) {
            try {
                refund = gateway.refund(transactionId, amount.doubleValue());
                if (refund != null && refund.success()) {
                    break;
                }
            } catch (Exception e) {
                log.error("Refund Gateway failed with exception", e);
            }
        }
        if (refund == null || !refund.success()) {
            log.error("ESCALATION: Refund failed after ticket supply failure: reason={}",
                    refund != null ? refund.errorMessage() : "All gateways failed");
        }
    }

    private void validatePurchasePolicy(Event event, ActiveOrder order, UUID memberId, LocalDate buyerDateOfBirth) {
        PurchaseContext ctx = new PurchaseContext(order, memberId, buyerDateOfBirth, event, java.util.Set.of());
        PolicyResult validation = event.getEventPurchasePolicy().isAllowed(ctx);
        if (!validation.allowed()) {
            throw new IllegalStateException("Purchase policy violation: "
                    + validation.errorCode() + " — " + validation.reason());
        }
    }

    private void validatePurchasePolicyOnReservation(Event event, ActiveOrder order, com.ticketing.domain.order.SelectionRequest request) {
        int additionalTickets = 0;
        for (com.ticketing.domain.order.SelectionRequest.SeatPick ignored : request.seats()) {
            additionalTickets++;
        }
        for (com.ticketing.domain.order.SelectionRequest.GAPick pick : request.gaQuantities()) {
            additionalTickets += pick.quantity();
        }

        java.util.Set<UUID> incomingSeatIds = request.seats().stream()
                .map(com.ticketing.domain.order.SelectionRequest.SeatPick::seatId)
                .collect(java.util.stream.Collectors.toSet());

        ActiveOrder simulatedOrder = order.simulateWithAdditionalTickets(additionalTickets);
        LocalDate buyerDob = getBuyerDateOfBirth(order.getMemberId());
        PurchaseContext ctx = new PurchaseContext(simulatedOrder, order.getMemberId(), buyerDob, event, incomingSeatIds);
        PolicyResult result = event.getEventPurchasePolicy().isAllowed(ctx);
        if (!result.allowed()) {
            throw new IllegalStateException(result.reason());
        }
    }

    private PaymentResult chargePayment(ActiveOrder order, Event event, BuyerContactSnapshot buyerContact, BigDecimal finalAmount) {
        PaymentResult payment = null;
        for (IPaymentGateway gateway : paymentGateways) {
            try {
                payment = gateway.charge(finalAmount,
                        new PaymentDetails(order.getId(), event.getId(), order.getMemberId(), buyerContact.getEmail()));
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
                tickets.add(new TicketRequest(event.getId().toString(),
                        item.getId().toString(), item.getSeatId().toString()));
            } else {
                for (int i = 0; i < item.getQuantity(); i++) {
                    tickets.add(new TicketRequest(event.getId().toString(),
                            item.getId() + "-" + (i + 1), null));
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

    private void validateSelection(com.ticketing.domain.order.SelectionRequest request, Event event) {
        if (request == null) throw new IllegalArgumentException("request is required");
        if (request.isEmpty()) {
            throw new IllegalArgumentException("selection must include at least one seat or quantity");
        }

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new IllegalStateException("Event is not selectable in status: " + event.getStatus());
        }

        for (com.ticketing.domain.order.SelectionRequest.SeatPick pick : request.seats()) {
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

        Map<UUID, Integer> totalsByZone = new HashMap<>();
        for (com.ticketing.domain.order.SelectionRequest.GAPick pick : request.gaQuantities()) {
            totalsByZone.merge(pick.zoneId(), pick.quantity(), Integer::sum);
        }
        for (var entry : totalsByZone.entrySet()) {
            InventoryZone zone = findZoneInEvent(event, entry.getKey());
            int requested = entry.getValue();
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

    private BigDecimal discountedCheckoutAmount(ActiveOrder order, Event event, String couponCode) {
        return event.getEventDiscountPolicy().priceAfterDiscount(order, couponCode, systemClock.now());
    }

    private static String normalizeCoupon(String couponCode) {
        return couponCode == null || couponCode.isBlank() ? null : couponCode.trim();
    }
}
