package com.ticketing.domain.services;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.ISystemClock;
import com.ticketing.application.SelectionRequest;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.ITicketSupplyGateway;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.BuyerContactSnapshot;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.order.OrderCheckoutDomainService;
import com.ticketing.domain.order.OrderItem;
import com.ticketing.domain.order.OrderStatus;
import com.ticketing.domain.order.TicketReservationDomainService;

@org.springframework.stereotype.Service
public class OrderDomainService {

    private static final Logger log = LoggerFactory.getLogger(OrderDomainService.class);

    private final IOrderRepository orderRepository;
    private final IEventRepository eventRepository;
    private final IMemberRepository memberRepository;
    private final ISystemClock systemClock;

    private final OrderCheckoutDomainService orderCheckoutService;
    private final TicketReservationDomainService ticketReservationService;
    private final com.ticketing.application.services.TicketSelectionService ticketSelectionService;

    public OrderDomainService(IOrderRepository orderRepository,
                                  IEventRepository eventRepository,
                                  IMemberRepository memberRepository,
                                  ISystemClock systemClock,
                                  List<IPaymentGateway> paymentGateways,
                                  List<ITicketSupplyGateway> ticketSupplyGateways,
                                  com.ticketing.application.services.TicketSelectionService ticketSelectionService) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
        this.systemClock = systemClock;
        this.ticketSelectionService = ticketSelectionService;

        this.orderCheckoutService = new OrderCheckoutDomainService(paymentGateways, ticketSupplyGateways, systemClock);
        this.ticketReservationService = new TicketReservationDomainService();
    }

    public UUID createOrder(UUID sessionId, UUID memberId, UUID eventId) {
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

    public UUID addSeatToOrder(UUID sessionId, UUID orderId, UUID zoneId, UUID seatId) {
        ActiveOrder order = findActiveOrder(orderId);
        validateOrderOwnership(sessionId, order);
        return addSelectionToOrder(sessionId, orderId,
                new SelectionRequest(order.getEventId(),
                        List.of(new SelectionRequest.SeatPick(zoneId, seatId)),
                        List.of())).get(0);
    }

    public UUID addGATicketsToOrder(UUID sessionId, UUID orderId, UUID zoneId, int quantity) {
        ActiveOrder order = findActiveOrder(orderId);
        validateOrderOwnership(sessionId, order);
        return addSelectionToOrder(sessionId, orderId,
                new SelectionRequest(order.getEventId(),
                        List.of(),
                        List.of(new SelectionRequest.GAPick(zoneId, quantity)))).get(0);
    }

    public List<UUID> addSelectionToOrder(UUID sessionId, UUID orderId, SelectionRequest request) {
        ActiveOrder order = findActiveOrder(orderId);
        validateOrderOwnership(sessionId, order);
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

    public UUID checkout(UUID sessionId, UUID orderId, UUID memberId, String couponCode) {
        ActiveOrder order = findActiveOrder(orderId);
        validateOrderOwnership(sessionId, order);
        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);

        if (order.getItems().isEmpty()) {
            log.warn("Failed to checkout order {}: no items in order", order.getId());
            throw new IllegalStateException("Cannot checkout an empty order");
        }

        BuyerContactSnapshot buyerContact = buyerContactFor(memberId);
        java.time.LocalDate buyerDob = memberId != null && memberRepository != null
                ? memberRepository.findById(memberId).map(Member::getDateOfBirth).orElse(null)
                : null;

        CompletedPurchase purchase;
        try {
            purchase = orderCheckoutService.processCheckout(order, event, buyerContact, couponCode, buyerDob);
        } catch (IllegalStateException e) {
            saveOrder(order);
            if (e.getMessage() != null && e.getMessage().contains("Ticket generation failed")) {
                ticketReservationService.releaseAllInventory(event, order);
                saveEvent(event);
            }
            log.warn("Failed to checkout order {}: {}", order.getId(), e.getMessage());
            throw e;
        }

        ticketReservationService.sellAllInventory(event, order);
        saveEvent(event);
        saveOrder(order);
        orderRepository.save(purchase);

        checkAndPublishSoldOut(event);
        log.info("Checkout complete: orderId={}, purchaseId={}, amount={}",
                order.getId(), purchase.purchaseId(), purchase.amount());
        return purchase.purchaseId();
    }

    public void removeItemFromOrder(UUID sessionId, UUID orderId, UUID itemId) {
        ActiveOrder order = findActiveOrder(orderId);
        validateOrderOwnership(sessionId, order);
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
        log.info("Item removed from order: orderId={}, itemId={}", order.getId(), itemId);
    }

    public void updateGAQuantity(UUID sessionId, UUID orderId, UUID zoneId, int newQuantity) {
        ActiveOrder order = findActiveOrder(orderId);
        validateOrderOwnership(sessionId, order);
        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);

        ticketReservationService.updateGAQuantity(order, event, zoneId, newQuantity);
        saveEvent(event);

        saveOrder(order);
        log.info("GA quantity updated: orderId={}, zoneId={}", order.getId(), zoneId);
    }

    public void cancelOrder(UUID sessionId, UUID orderId) {
        ActiveOrder order = findActiveOrder(orderId);
        validateOrderOwnership(sessionId, order);
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed order");
        }

        Event event = findEvent(order.getEventId());
        ticketReservationService.releaseAllInventory(event, order);
        saveEvent(event);

        order.cancel();
        saveOrder(order);
        log.info("Order cancelled: orderId={}", order.getId());
    }

    public void refundEventPurchases(UUID eventId) {
        List<CompletedPurchase> purchases = orderRepository.findCompletedByEventId(eventId);
        for (CompletedPurchase purchase : purchases) {
            orderCheckoutService.refundPayment(purchase.transactionId(), purchase.amount());
        }
    }

    public com.ticketing.application.dto.ActiveOrderDto getActiveOrderDto(UUID sessionId, UUID orderId) {
        ActiveOrder order = findActiveOrder(orderId);
        validateOrderOwnership(sessionId, order);
        return new com.ticketing.application.dto.ActiveOrderDto(
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

    public List<com.ticketing.application.dto.PurchaseRecordDTO> getPurchaseHistory(UUID memberId) {
        List<CompletedPurchase> purchases = orderRepository.findCompletedByMemberId(memberId);
        List<com.ticketing.application.dto.PurchaseRecordDTO> result = new ArrayList<>();
        for (CompletedPurchase p : purchases) {
            result.add(com.ticketing.application.dto.PurchaseRecordDTO.from(p));
        }
        return result;
    }

    public ActiveOrder findActiveOrder(UUID orderId) {
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

    private Event findEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", eventId);
                    return new IllegalArgumentException("Event not found: " + eventId);
                });
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

    private void checkAndPublishSoldOut(Event event) {
        if (!event.hasAvailableTickets() && event.isPublished()) {
            event.markSoldOut();
            saveEvent(event);
        }
    }
}
