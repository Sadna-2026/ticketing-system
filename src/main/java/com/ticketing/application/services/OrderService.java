package com.ticketing.application.services;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.ISystemClock;
import com.ticketing.application.SelectionRequest;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.QueueEntryDto;
import com.ticketing.application.dto.VirtualQueueDto;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.ITicketSupplyGateway;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.BuyerContactSnapshot;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.order.OrderCheckoutDomainService;
import com.ticketing.domain.order.OrderItem;
import com.ticketing.domain.order.OrderStatus;
import com.ticketing.domain.order.TicketReservationDomainService;
import com.ticketing.domain.queue.IQueueRepository;
import com.ticketing.domain.queue.QueueConfig;
import com.ticketing.domain.queue.QueueEntry;
import com.ticketing.domain.queue.VirtualQueue;
import com.ticketing.infrastructure.gateway.StubPaymentGateway;
import com.ticketing.infrastructure.gateway.StubTicketSupplyGateway;

@org.springframework.stereotype.Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final ISessionTokenService sessionTokenService;
    private final IOrderRepository orderRepository;
    private final IEventRepository eventRepository;
    private final IQueueRepository queueRepository;
    private final ISystemClock systemClock;
    private final IMemberRepository memberRepository;
    private final TicketSelectionService ticketSelectionService;

    private final OrderCheckoutDomainService orderCheckoutService;
    private final TicketReservationDomainService ticketReservationService;

    public OrderService(IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        IEventRepository eventRepository,
                        ISystemClock systemClock) {
        this(orderRepository, sessionTokenService, eventRepository, systemClock,
                null, null, List.of(new StubPaymentGateway()), List.of(new StubTicketSupplyGateway()),
                new TicketSelectionService(eventRepository));
    }

    public OrderService(IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        IEventRepository eventRepository,
                        ISystemClock systemClock,
                        IMemberRepository memberRepository,
                        List<IPaymentGateway> paymentGateways,
                        ITicketSupplyGateway ticketSupplyGateway) {
        this(orderRepository, sessionTokenService, eventRepository, systemClock,
                memberRepository, null, paymentGateways, List.of(ticketSupplyGateway),
                new TicketSelectionService(eventRepository));
    }

    public OrderService(IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        IEventRepository eventRepository,
                        ISystemClock systemClock,
                        IMemberRepository memberRepository,
                        List<IPaymentGateway> paymentGateways,
                        List<ITicketSupplyGateway> ticketSupplyGateways,
                        TicketSelectionService ticketSelectionService) {
        this(orderRepository, sessionTokenService, eventRepository, systemClock,
                memberRepository, null, paymentGateways, ticketSupplyGateways,
                ticketSelectionService);
    }
    
    @org.springframework.beans.factory.annotation.Autowired
    public OrderService(IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        IEventRepository eventRepository,
                        ISystemClock systemClock,
                        IMemberRepository memberRepository,
                        IQueueRepository queueRepository,
                        List<IPaymentGateway> paymentGateways,
                        List<ITicketSupplyGateway> ticketSupplyGateways,
                        TicketSelectionService ticketSelectionService) {
        if (orderRepository == null) throw new IllegalArgumentException("orderRepository is required");
        if (sessionTokenService == null) throw new IllegalArgumentException("sessionTokenService is required");
        if (eventRepository == null) throw new IllegalArgumentException("eventRepository is required");
        if (systemClock == null) throw new IllegalArgumentException("systemClock is required");
        if (paymentGateways == null || paymentGateways.isEmpty()) throw new IllegalArgumentException("paymentGateways is required");
        if (ticketSupplyGateways == null || ticketSupplyGateways.isEmpty()) throw new IllegalArgumentException("ticketSupplyGateways is required");
        if (ticketSelectionService == null) throw new IllegalArgumentException("ticketSelectionService is required");

        this.orderRepository = orderRepository;
        this.sessionTokenService = sessionTokenService;
        this.eventRepository = eventRepository;
        this.queueRepository = queueRepository;
        this.systemClock = systemClock;
        this.memberRepository = memberRepository;
        this.ticketSelectionService = ticketSelectionService;

        this.orderCheckoutService = new OrderCheckoutDomainService(paymentGateways, ticketSupplyGateways, systemClock);
        this.ticketReservationService = new TicketReservationDomainService();
    }

    public UUID createOrder(String token, UUID eventId) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);

        orderRepository.findActiveBySessionId(sessionId).ifPresent(existing -> {
            log.warn("Failed to create order: session {} already has an active order {}", sessionId, existing.getId());
            throw new IllegalStateException("Session already has an active order");
        });

        Event event = findEvent(eventId);
        if (!event.isPublished()) {
            log.warn("Failed to create order: event {} is not available for purchase", eventId);
            throw new IllegalStateException("Event is not available for purchase");
        }

        ActiveOrder order = new ActiveOrder(UUID.randomUUID(), sessionId, memberId, eventId, systemClock.now());
        saveOrder(order);

        log.info("Order created: orderId={}, sessionId={}, eventId={}", order.getId(), sessionId, eventId);
        return order.getId();
    }

    public UUID addSeatToOrder(String token, UUID orderId, UUID zoneId, UUID seatId) {
        validateToken(token);
        return addSelectionToOrder(token, orderId,
                new SelectionRequest(findActiveOrder(orderId).getEventId(),
                        List.of(new SelectionRequest.SeatPick(zoneId, seatId)),
                        List.of())).get(0);
    }

    public UUID addGATicketsToOrder(String token, UUID orderId, UUID zoneId, int quantity) {
        validateToken(token);
        return addSelectionToOrder(token, orderId,
                new SelectionRequest(findActiveOrder(orderId).getEventId(),
                        List.of(),
                        List.of(new SelectionRequest.GAPick(zoneId, quantity)))).get(0);
    }

    public List<UUID> addSelectionToOrder(String token, UUID orderId, SelectionRequest request) {
        validateToken(token);
        if (request == null) throw new IllegalArgumentException("request is required");
        ActiveOrder order = findActiveOrder(orderId);
        validateOrderOwnership(token, order);
        if (!order.getEventId().equals(request.eventId())) {
            log.warn("Failed to add selection to order: selection event {} does not match order event {}", request.eventId(), order.getEventId());
            throw new IllegalArgumentException("Selection event does not match order event");
        }

        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);
        ticketSelectionService.validateSelection(request);

        List<UUID> itemIds = new ArrayList<>();
        for (SelectionRequest.SeatPick pick : request.seats()) {
            itemIds.add(ticketReservationService.lockSeat(order, event, pick.zoneId(), pick.seatId()));
        }
        for (SelectionRequest.GAPick pick : request.gaQuantities()) {
            itemIds.add(ticketReservationService.lockGA(order, event, pick.zoneId(), pick.quantity()));
        }

        saveEvent(event);
        saveOrder(order);
        checkAndPublishSoldOut(event);
        return itemIds;
    }

    public UUID checkout(String token, UUID orderId, String couponCode) {
        validateToken(token);
        UUID memberId = sessionTokenService.extractMemberId(token);

        ActiveOrder order = findActiveOrder(orderId);
        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);

        if (order.getItems().isEmpty()) {
            log.warn("Failed to checkout order {}: no items in order", orderId);
            throw new IllegalStateException("Cannot checkout an empty order");
        }

        BuyerContactSnapshot buyerContact = buyerContactFor(memberId);
        
        CompletedPurchase purchase;
        try {
            purchase = orderCheckoutService.processCheckout(order, event, buyerContact, couponCode);
        } catch (IllegalStateException e) {
            saveOrder(order);
            if (e.getMessage() != null && e.getMessage().contains("Ticket generation failed")) {
                ticketReservationService.releaseAllInventory(event, order);
                saveEvent(event);
            }
            log.warn("Failed to checkout order {}: {}", orderId, e.getMessage());
            throw e;
        }

        ticketReservationService.sellAllInventory(event, order);
        saveEvent(event);
        saveOrder(order);
        orderRepository.save(purchase);

        checkAndPublishSoldOut(event);
        log.info("Checkout complete: orderId={}, purchaseId={}, amount={}",
                orderId, purchase.purchaseId(), purchase.amount());
        return purchase.purchaseId();
    }

    public CompletedPurchase getCompletedPurchase(UUID purchaseId) {
        return orderRepository.findCompletedById(purchaseId)
                .orElseThrow(() -> { log.warn("Completed purchase not found: purchaseId={}", purchaseId);
                    return new IllegalArgumentException("Completed purchase not found: " + purchaseId); });
    }

    private void validateToken(String token) {
        if (token == null || token.isBlank()) {
                log.warn("Failed to validate token: token is null or blank");
            throw new IllegalArgumentException("Authentication token is required");
        }
        if (!sessionTokenService.isValid(token)) {
            log.warn("Failed to validate token: token is invalid or expired");
            throw new IllegalArgumentException("Invalid or expired authentication token");
        }
    }

    private Event findEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", eventId);
                    return new IllegalArgumentException("Event not found: " + eventId);
                });
    }

    private ActiveOrder findActiveOrder(UUID orderId) {
        ActiveOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found: orderId={}", orderId);
                    return new IllegalArgumentException("Order not found: " + orderId);
                });
        if (!order.isActive()) {
            log.warn("Order is not active: orderId={}", orderId);
            throw new IllegalStateException("Order is not active (status: " + order.getStatus() + ")");
        }
        return order;
    }

    private void saveEvent(Event event) {
        try {
            eventRepository.save(event);
        } catch (OptimisticLockException ex) {
            log.warn("Event save conflict during order flow: eventId={}", event.getId());
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

    private void validateOrderNotExpired(ActiveOrder order, Event event) {
        if (order.isExpiredAt(systemClock.now(), event.getLockTimerDuration().getDuration())) {
            log.warn("Order has expired: orderId={}, eventId={}", order.getId(), event.getId());
            throw new IllegalStateException("Order has expired");
        }
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

    public void refundEventPurchases(UUID eventId) {
        List<CompletedPurchase> purchases = orderRepository.findCompletedByEventId(eventId);
        for (CompletedPurchase purchase : purchases) {
            orderCheckoutService.refundPayment(purchase.transactionId(), purchase.amount());
        }
    }

    private void checkAndPublishSoldOut(Event event) {
        if (!event.hasAvailableTickets() && event.isPublished()) {
            event.markSoldOut();
            saveEvent(event);
        }
    }

    public void removeItemFromOrder(String token, UUID orderId, UUID itemId) {
        validateToken(token);

        ActiveOrder order = findActiveOrder(orderId);
        validateOrderOwnership(token, order);
        Event event = findEvent(order.getEventId());

        OrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Item not found: itemId={}", itemId);
                    return new IllegalArgumentException("Item not found: " + itemId);
                });

        ticketReservationService.releaseInventoryForItem(event, item);
        saveEvent(event);

        order.removeItem(itemId);
        saveOrder(order);
        log.info("Item removed from order: orderId={}, itemId={}", orderId, itemId);
    }

    public ActiveOrderDto getActiveOrder(String token, UUID orderId) {
        ActiveOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found: orderId={}", orderId);
                    return new IllegalArgumentException("Order not found: " + orderId);
                });
        validateOrderOwnership(token, order);
        return new ActiveOrderDto(
                order.getId(),
                order.getSessionId(),
                order.getMemberId(),
                order.getEventId(),
                order.getCreatedAt(),
                order.getStatus().name(),
                order.getItemsDto(),
                order.getTotalPrice()
        );
    }


    public void updateGAQuantity(String token, UUID orderId, UUID zoneId, int newQuantity) {
        validateToken(token);

        ActiveOrder order = findActiveOrder(orderId);
        validateOrderOwnership(token, order);
        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);

        ticketReservationService.updateGAQuantity(order, event, zoneId, newQuantity);
        saveEvent(event);

        saveOrder(order);
        log.info("GA quantity updated: orderId={}, zoneId={}", orderId, zoneId);
    }

    public void cancelOrder(String token, UUID orderId) {
        validateToken(token);

        ActiveOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found: orderId={}", orderId);
                    return new IllegalArgumentException("Order not found: " + orderId);
                });

        validateOrderOwnership(token, order);
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed order");
        }

        Event event = findEvent(order.getEventId());
        ticketReservationService.releaseAllInventory(event, order);
        saveEvent(event);

        order.cancel();
        saveOrder(order);
        log.info("Order cancelled: orderId={}", orderId);
    }

    private void validateOrderOwnership(String token, ActiveOrder order) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token is required to access an order");
        }
        UUID sessionId = sessionTokenService.extractSessionId(token);
        if (!order.getSessionId().equals(sessionId)) {
            throw new IllegalStateException("Order does not belong to this session");
        }
    }

    public List<PurchaseRecordDTO> getPurchaseHistory(String token) {
        validateToken(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        if (memberId == null) {
            throw new SecurityException("Guests do not have a purchase history");
        }

        List<CompletedPurchase> purchases = orderRepository.findCompletedByMemberId(memberId);

        List<PurchaseRecordDTO> result = new ArrayList<>();
        for (CompletedPurchase p : purchases) {
            result.add(PurchaseRecordDTO.from(p));
        }

        return result;
    }

    // ── Queue management ─────────────────────────────────────────────────

    /**
     * Creates a virtual queue for an event (Admin action).
     */
    public UUID createQueue(String token, UUID eventId, int threshold, int flowRate) {
        UUID adminId = validateAdmin(token);
        log.info("Creating queue: adminId={}, eventId={}, threshold={}, flowRate={}", adminId, eventId, threshold, flowRate);

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
     * Determines if a user should be queued, and if so, adds them.
     * No authentication required (guests can be queued).
     *
     * @return a QueueEntryDto if queued, null if user can enter directly
     */
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

    /**
     * Admits the next batch of users from the queue.
     */
    public List<QueueEntryDto> admitNextBatch(String token, UUID eventId) {
        validateAdmin(token);
        log.info("Admitting next batch: eventId={}", eventId);

        VirtualQueue queue = findQueueByEvent(eventId);
        List<QueueEntry> admitted = queue.admitNextBatch();
        saveQueue(queue);
        log.info("Admitted {} users from queue for eventId={}", admitted.size(), eventId);
        return admitted.stream()
                .map(QueueEntry::toQueueDto)
                .collect(Collectors.toList());
    }

    /**
     * Records that a user has left the active purchasing phase.
     */
    public void userLeft(UUID eventId) {
        VirtualQueue queue = queueRepository.findByEventId(eventId).orElse(null);
        if (queue != null) {
            queue.userLeft();
            saveQueue(queue);
        }
    }

    /**
     * Updates queue configuration (Admin action).
     */
    public void updateQueueConfig(String token, UUID eventId, int threshold, int flowRate) {
        UUID adminId = validateAdmin(token);
        log.info("Updating queue config: adminId={}, eventId={}, threshold={}, flowRate={}", adminId, eventId, threshold, flowRate);

        VirtualQueue queue = findQueueByEvent(eventId);
        queue.updateConfig(new QueueConfig(threshold, flowRate));
        saveQueue(queue);
        log.info("Queue config updated: eventId={}", eventId);
    }

    /**
     * Flushes/clears a queue (Admin emergency action).
     */
    public void flushQueue(String token, UUID eventId) {
        UUID adminId = validateAdmin(token);
        log.info("Flushing queue: adminId={}, eventId={}", adminId, eventId);

        VirtualQueue queue = findQueueByEvent(eventId);
        queue.flush();
        saveQueue(queue);
        log.info("Queue flushed: eventId={}", eventId);
    }

    /**
     * Gets all active virtual queues (Admin monitoring).
     */
    public List<VirtualQueueDto> getAllActiveQueues(String token) {
        validateAdmin(token);
        log.info("Getting all active queues");
        return queueRepository.findAllActive().stream()
                .map(VirtualQueue::toVirtualQueueDto)
                .collect(Collectors.toList());
    }

    /**
     * Gets the queue for a specific event.
     */
    public VirtualQueueDto getQueueForEvent(UUID eventId) {
        return findQueueByEvent(eventId).toVirtualQueueDto();
    }

    private VirtualQueue findQueueByEvent(UUID eventId) {
        return queueRepository.findByEventId(eventId)
                .orElseThrow(() -> new IllegalStateException("No virtual queue for event: " + eventId));
    }

    private UUID validateAdmin(String token) {
        UUID adminId = validateTokenAndExtractMemberId(token);
        if (!sessionTokenService.extractPermissions(token).contains("Admin")) {
            throw new IllegalStateException("Only System Admins can perform this action");
        }
        return adminId;
    }

    private UUID validateTokenAndExtractMemberId(String token) {
        validateToken(token);
        return sessionTokenService.extractMemberId(token);
    }
}

