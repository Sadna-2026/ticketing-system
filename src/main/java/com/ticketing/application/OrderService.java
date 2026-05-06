package com.ticketing.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.PolicyResult;
import com.ticketing.domain.gateway.CustomerInfo;
import com.ticketing.domain.gateway.PaymentDetails;
import com.ticketing.domain.gateway.PaymentResult;
import com.ticketing.domain.gateway.RefundResult;
import com.ticketing.domain.gateway.SupplyResult;
import com.ticketing.domain.gateway.TicketRequest;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.BuyerContactSnapshot;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.order.OrderItem;
import com.ticketing.infrastructure.Interface.IEventRepository;
import com.ticketing.infrastructure.Interface.IPaymentGateway;
import com.ticketing.infrastructure.Interface.ITicketSupplyGateway;
import com.ticketing.infrastructure.gateway.StubPaymentGateway;
import com.ticketing.infrastructure.gateway.StubTicketSupplyGateway;

public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final ISessionTokenService sessionTokenService;
    private final IOrderRepository orderRepository;
    private final IEventRepository eventRepository;
    private final ISystemClock systemClock;
    private final IMemberRepository memberRepository;
    private final IPaymentGateway paymentGateway;
    private final ITicketSupplyGateway ticketSupplyGateway;
    private final TicketSelectionService ticketSelectionService;

    public OrderService(IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        IEventRepository eventRepository,
                        ISystemClock systemClock) {
        this(orderRepository, sessionTokenService, eventRepository, systemClock,
                null, new StubPaymentGateway(), new StubTicketSupplyGateway(),
                new TicketSelectionService(eventRepository));
    }

    public OrderService(IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        IEventRepository eventRepository,
                        ISystemClock systemClock,
                        IMemberRepository memberRepository,
                        IPaymentGateway paymentGateway,
                        ITicketSupplyGateway ticketSupplyGateway) {
        this(orderRepository, sessionTokenService, eventRepository, systemClock,
                memberRepository, paymentGateway, ticketSupplyGateway,
                new TicketSelectionService(eventRepository));
    }

    public OrderService(IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        IEventRepository eventRepository,
                        ISystemClock systemClock,
                        IMemberRepository memberRepository,
                        IPaymentGateway paymentGateway,
                        ITicketSupplyGateway ticketSupplyGateway,
                        TicketSelectionService ticketSelectionService) {
        if (orderRepository == null) throw new IllegalArgumentException("orderRepository is required");
        if (sessionTokenService == null) throw new IllegalArgumentException("sessionTokenService is required");
        if (eventRepository == null) throw new IllegalArgumentException("eventRepository is required");
        if (systemClock == null) throw new IllegalArgumentException("systemClock is required");
        if (paymentGateway == null) throw new IllegalArgumentException("paymentGateway is required");
        if (ticketSupplyGateway == null) throw new IllegalArgumentException("ticketSupplyGateway is required");
        if (ticketSelectionService == null) throw new IllegalArgumentException("ticketSelectionService is required");

        this.orderRepository = orderRepository;
        this.sessionTokenService = sessionTokenService;
        this.eventRepository = eventRepository;
        this.systemClock = systemClock;
        this.memberRepository = memberRepository;
        this.paymentGateway = paymentGateway;
        this.ticketSupplyGateway = ticketSupplyGateway;
        this.ticketSelectionService = ticketSelectionService;
    }

    public UUID createOrder(String token, UUID eventId) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);

        orderRepository.findActiveBySessionId(sessionId).ifPresent(existing -> {
            throw new IllegalStateException("Session already has an active order");
        });

        Event event = findEvent(eventId);
        if (!event.isPublished()) {
            throw new IllegalStateException("Event is not available for purchase");
        }

        ActiveOrder order = new ActiveOrder(UUID.randomUUID(), sessionId, memberId, eventId, systemClock.now());
        orderRepository.save(order);

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
        if (!order.getEventId().equals(request.eventId())) {
            throw new IllegalArgumentException("Selection event does not match order event");
        }

        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);
        ticketSelectionService.validateSelection(request);

        List<UUID> itemIds = new ArrayList<>();
        for (SelectionRequest.SeatPick pick : request.seats()) {
            itemIds.add(lockSeat(order, event, pick.zoneId(), pick.seatId()));
        }
        for (SelectionRequest.GAPick pick : request.gaQuantities()) {
            itemIds.add(lockGA(order, event, pick.zoneId(), pick.quantity()));
        }

        eventRepository.save(event);
        orderRepository.save(order);
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
            throw new IllegalStateException("Cannot checkout an empty order");
        }

        BuyerContactSnapshot buyerContact = buyerContactFor(memberId);
        validatePurchasePolicy(event, order, memberId);

        BigDecimal discountAmount = event.getEventDiscountPolicy().applyTo(order, couponCode, systemClock.now());
        BigDecimal finalAmount = order.getTotalPrice().subtract(discountAmount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        order.startCheckout();
        orderRepository.save(order);

        PaymentResult payment = paymentGateway.charge(finalAmount,
                new PaymentDetails(order.getId(), event.getId(), memberId, buyerContact.getEmail()));
        if (!payment.success()) {
            order.revertToActive();
            orderRepository.save(order);
            throw new IllegalStateException("Payment failed: " + payment.errorMessage());
        }

        SupplyResult supply = ticketSupplyGateway.issueTickets(ticketRequests(order, event),
                new CustomerInfo(memberId == null ? null : memberId.toString(),
                        buyerContact.getEmail(), buyerContact.getUsername()));
        if (!supply.success()) {
            refundPayment(payment.transactionId(), finalAmount);
            releaseAllInventory(event, order);
            eventRepository.save(event);
            order.cancel();
            orderRepository.save(order);
            throw new IllegalStateException("Ticket generation failed. Payment has been refunded: "
                    + supply.errorMessage());
        }

        sellAllInventory(event, order);
        eventRepository.save(event);

        order.complete();
        orderRepository.save(order);

        CompletedPurchase purchase = new CompletedPurchase(
                UUID.randomUUID(),
                event.getId(),
                event.getName(),
                event.getCompanyName(),
                memberId,
                payment.transactionId(),
                finalAmount,
                systemClock.now());
        orderRepository.save(purchase);

        checkAndPublishSoldOut(event);
        log.info("Checkout complete: orderId={}, purchaseId={}, amount={}",
                orderId, purchase.purchaseId(), finalAmount);
        return purchase.purchaseId();
    }

    public CompletedPurchase getCompletedPurchase(UUID purchaseId) {
        return orderRepository.findCompletedById(purchaseId)
                .orElseThrow(() -> new IllegalArgumentException("Completed purchase not found: " + purchaseId));
    }

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

    private void validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token is required");
        }
        if (!sessionTokenService.isValid(token)) {
            throw new IllegalArgumentException("Invalid or expired authentication token");
        }
    }

    private Event findEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    }

    private ActiveOrder findActiveOrder(UUID orderId) {
        ActiveOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (!order.isActive()) {
            throw new IllegalStateException("Order is not active (status: " + order.getStatus() + ")");
        }
        return order;
    }

    private void validateOrderNotExpired(ActiveOrder order, Event event) {
        if (order.isExpiredAt(systemClock.now(), event.getLockTimerDuration().getDuration())) {
            throw new IllegalStateException("Order has expired");
        }
    }

    private void validatePurchasePolicy(Event event, ActiveOrder order, UUID memberId) {
        PolicyResult validation = event.getEventPurchasePolicy().isAllowed(order, memberId);
        if (!validation.allowed()) {
            throw new IllegalStateException("Purchase policy violation: "
                    + validation.errorCode() + " " + validation.reason());
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

    private void refundPayment(String transactionId, BigDecimal amount) {
        RefundResult refund = paymentGateway.refund(transactionId, amount.doubleValue());
        if (!refund.success()) {
            log.error("Refund failed after ticket supply failure: transactionId={}, reason={}",
                    transactionId, refund.errorMessage());
        }
    }

    private void releaseAllInventory(Event event, ActiveOrder order) {
        for (OrderItem item : order.getItems()) {
            InventoryZone zone = event.findZone(item.getZoneId());
            if (item.isAssignedSeat()) {
                zone.releaseSeat(item.getSeatId());
            } else {
                zone.releaseGA(item.getQuantity());
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
            eventRepository.save(event);
        }
    }
}
