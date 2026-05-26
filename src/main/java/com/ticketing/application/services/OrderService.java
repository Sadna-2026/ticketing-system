package com.ticketing.application.services;

import java.util.List;
import java.util.UUID;

import com.ticketing.application.ISystemClock;
import com.ticketing.application.SelectionRequest;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.QueueEntryDto;
import com.ticketing.application.dto.VirtualQueueDto;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.ITicketSupplyGateway;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.queue.IQueueRepository;
import com.ticketing.domain.services.OrderDomainService;
import com.ticketing.domain.services.QueueDomainService;
import com.ticketing.infrastructure.gateway.StubPaymentGateway;
import com.ticketing.infrastructure.gateway.StubTicketSupplyGateway;

@org.springframework.stereotype.Service
public class OrderService {
    private final ISessionTokenService sessionTokenService;
    private final OrderDomainService domainService;
    private final QueueDomainService queueDomainService;

    // Backward-compatible constructors

    public OrderService(IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        IEventRepository eventRepository,
                        ISystemClock systemClock) {
        this(sessionTokenService,
             new OrderDomainService(orderRepository, eventRepository, null, systemClock,
                     List.of(new StubPaymentGateway()), List.of(new StubTicketSupplyGateway())),
             null);
    }

    public OrderService(IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        IEventRepository eventRepository,
                        ISystemClock systemClock,
                        IMemberRepository memberRepository,
                        List<IPaymentGateway> paymentGateways,
                        ITicketSupplyGateway ticketSupplyGateway) {
        this(sessionTokenService,
             new OrderDomainService(orderRepository, eventRepository, memberRepository, systemClock,
                     paymentGateways, List.of(ticketSupplyGateway)),
             null);
    }

    public OrderService(IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        IEventRepository eventRepository,
                        ISystemClock systemClock,
                        IMemberRepository memberRepository,
                        List<IPaymentGateway> paymentGateways,
                        List<ITicketSupplyGateway> ticketSupplyGateways) {
        this(sessionTokenService,
             new OrderDomainService(orderRepository, eventRepository, memberRepository, systemClock,
                     paymentGateways, ticketSupplyGateways),
             null);
    }

    public OrderService(IOrderRepository orderRepository,
                        ISessionTokenService sessionTokenService,
                        IEventRepository eventRepository,
                        ISystemClock systemClock,
                        IMemberRepository memberRepository,
                        IQueueRepository queueRepository,
                        List<IPaymentGateway> paymentGateways,
                        List<ITicketSupplyGateway> ticketSupplyGateways) {
        this(sessionTokenService,
             new OrderDomainService(orderRepository, eventRepository, memberRepository, systemClock,
                     paymentGateways, ticketSupplyGateways),
             new QueueDomainService(queueRepository, eventRepository, systemClock));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OrderService(ISessionTokenService sessionTokenService,
                        OrderDomainService domainService,
                        QueueDomainService queueDomainService) {
        if (sessionTokenService == null) throw new IllegalArgumentException("sessionTokenService is required");
        if (domainService == null) throw new IllegalArgumentException("domainService is required");

        this.sessionTokenService = sessionTokenService;
        this.domainService = domainService;
        this.queueDomainService = queueDomainService;
    }

    public UUID createOrder(String token, UUID eventId) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        return domainService.createOrder(sessionId, memberId, eventId);
    }

    public UUID addSeatToOrder(String token, UUID orderId, UUID zoneId, UUID seatId) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        return domainService.addSeatToOrder(sessionId, orderId, zoneId, seatId);
    }

    public UUID addGATicketsToOrder(String token, UUID orderId, UUID zoneId, int quantity) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        return domainService.addGATicketsToOrder(sessionId, orderId, zoneId, quantity);
    }

    public List<UUID> addSelectionToOrder(String token, UUID orderId, SelectionRequest request) {
        validateToken(token);
        if (request == null) throw new IllegalArgumentException("request is required");
        UUID sessionId = sessionTokenService.extractSessionId(token);
        com.ticketing.domain.order.SelectionRequest domainRequest = new com.ticketing.domain.order.SelectionRequest(
                request.eventId(),
                request.seats().stream()
                        .map(s -> new com.ticketing.domain.order.SelectionRequest.SeatPick(s.zoneId(), s.seatId()))
                        .toList(),
                request.gaQuantities().stream()
                        .map(g -> new com.ticketing.domain.order.SelectionRequest.GAPick(g.zoneId(), g.quantity()))
                        .toList()
        );
        return domainService.addSelectionToOrder(sessionId, orderId, domainRequest);
    }

    public UUID checkout(String token, UUID orderId, String couponCode) {
        validateToken(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        return domainService.checkout(sessionId, orderId, memberId, couponCode);
    }

    public void refundEventPurchases(UUID eventId) {
        domainService.refundEventPurchases(eventId);
    }

    public void removeItemFromOrder(String token, UUID orderId, UUID itemId) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        domainService.removeItemFromOrder(sessionId, orderId, itemId);
    }

    public void updateGAQuantity(String token, UUID orderId, UUID zoneId, int newQuantity) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        domainService.updateGAQuantity(sessionId, orderId, zoneId, newQuantity);
    }

    public void cancelOrder(String token, UUID orderId) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        domainService.cancelOrder(sessionId, orderId);
    }

    public ActiveOrderDto getActiveOrder(String token, UUID orderId) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        com.ticketing.domain.order.ActiveOrder order = domainService.getActiveOrder(sessionId, orderId);
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

    public List<PurchaseRecordDTO> getPurchaseHistory(String token) {
        validateToken(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        if (memberId == null) {
            throw new SecurityException("User must be logged in to view purchase history");
        }
        List<com.ticketing.domain.order.CompletedPurchase> purchases = domainService.getPurchaseHistory(memberId);
        List<PurchaseRecordDTO> result = new java.util.ArrayList<>();
        for (com.ticketing.domain.order.CompletedPurchase p : purchases) {
            result.add(PurchaseRecordDTO.from(p));
        }
        return result;
    }

    // Virtual Queue methods

    public UUID createQueue(String token, UUID eventId, int threshold, int flowRate) {
        validateToken(token);
        return queueDomainService.createQueue(eventId, threshold, flowRate);
    }

    public QueueEntryDto tryEnterOrQueue(UUID eventId, UUID sessionId) {
        return queueDomainService.tryEnterOrQueue(eventId, sessionId);
    }

    public List<QueueEntryDto> admitNextBatch(String token, UUID eventId) {
        validateToken(token);
        return queueDomainService.admitNextBatch(eventId);
    }

    public void userLeft(UUID eventId) {
        queueDomainService.userLeft(eventId);
    }

    public void updateQueueConfig(String token, UUID eventId, int threshold, int flowRate) {
        validateToken(token);
        queueDomainService.updateQueueConfig(eventId, threshold, flowRate);
    }

    public void flushQueue(String token, UUID eventId) {
        validateToken(token);
        queueDomainService.flushQueue(eventId);
    }

    public List<VirtualQueueDto> getAllActiveQueues(String token) {
        validateToken(token);
        return queueDomainService.getAllActiveQueues();
    }

    public VirtualQueueDto getQueueForEvent(UUID eventId) {
        return queueDomainService.getQueueForEvent(eventId);
    }

    private void validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token is required to access an order");
        }
        if (!sessionTokenService.isValid(token)) {
            throw new IllegalArgumentException("Authentication token is invalid");
        }
    }
}
