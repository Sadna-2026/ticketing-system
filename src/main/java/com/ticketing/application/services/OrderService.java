package com.ticketing.application.services;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.SelectionRequest;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.QueueEntryDto;
import com.ticketing.application.dto.VirtualQueueDto;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.OrderCheckoutDomainService;
import com.ticketing.domain.order.TicketReservationDomainService;
import com.ticketing.domain.services.OrderTimeDomainService;
import com.ticketing.domain.services.QueueDomainService;

@org.springframework.stereotype.Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final ISessionTokenService sessionTokenService;
    private final TicketReservationDomainService ticketReservationDomainService;
    private final OrderCheckoutDomainService orderCheckoutDomainService;
    private final QueueDomainService queueDomainService;
    private final OrderTimeDomainService orderTimeDomainService;
    private final INotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired
    public OrderService(ISessionTokenService sessionTokenService,
            TicketReservationDomainService ticketReservationDomainService,
            OrderCheckoutDomainService orderCheckoutDomainService,
            QueueDomainService queueDomainService,
            OrderTimeDomainService orderTimeDomainService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) INotificationService notificationService) {
        if (sessionTokenService == null)
            throw new IllegalArgumentException("sessionTokenService is required");
        if (ticketReservationDomainService == null)
            throw new IllegalArgumentException("ticketReservationService is required");
        if (orderCheckoutDomainService == null)
            throw new IllegalArgumentException("orderCheckoutService is required");

        this.sessionTokenService = sessionTokenService;
        this.ticketReservationDomainService = ticketReservationDomainService;
        this.orderCheckoutDomainService = orderCheckoutDomainService;
        this.queueDomainService = queueDomainService;
        this.orderTimeDomainService = orderTimeDomainService;
        this.notificationService = notificationService;
    }

    public UUID createOrder(String token, UUID eventId) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        return ticketReservationDomainService.createOrder(sessionId, memberId, eventId);
    }

    public UUID addSeatToOrder(String token, UUID eventId, UUID zoneId, UUID seatId) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        return ticketReservationDomainService.addSeatToOrder(sessionId, memberId, eventId, zoneId, seatId);
    }

    public UUID addGATicketsToOrder(String token, UUID eventId, UUID zoneId, int quantity) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        return ticketReservationDomainService.addGATicketsToOrder(sessionId, memberId, eventId, zoneId, quantity);
    }

    public List<UUID> addSelectionToOrder(String token, SelectionRequest request) {
        validateToken(token);
        if (request == null)
            throw new IllegalArgumentException("request is required");
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        ActiveOrder order = ticketReservationDomainService.findOrCreateActiveOrder(sessionId, memberId,
                request.eventId());
        com.ticketing.domain.order.SelectionRequest domainRequest = new com.ticketing.domain.order.SelectionRequest(
                request.eventId(),
                request.seats().stream()
                        .map(s -> new com.ticketing.domain.order.SelectionRequest.SeatPick(s.zoneId(), s.seatId()))
                        .toList(),
                request.gaQuantities().stream()
                        .map(g -> new com.ticketing.domain.order.SelectionRequest.GAPick(g.zoneId(), g.quantity()))
                        .toList());
        return ticketReservationDomainService.addSelectionToOrder(sessionId, order, domainRequest);
    }

    public UUID checkout(String token, String couponCode) {
        validateToken(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        ActiveOrder order = ticketReservationDomainService.getValidatedActiveOrder(sessionId, memberId);
        UUID purchaseId;
        try {
            purchaseId = orderCheckoutDomainService.checkout(sessionId, order.getId(), memberId, couponCode);
        } catch (IllegalStateException e) {
            if (notificationService != null && memberId != null) {
                notificationService.notify(memberId.toString(), "Checkout failed: " + e.getMessage());
            }
            throw e;
        }
        if (notificationService != null && memberId != null) {
            notificationService.notify(memberId.toString(), "Your checkout was completed successfully.");
        }
        return purchaseId;
    }

    public List<CompletedPurchase> refundEventPurchases(UUID eventId) {
        List<CompletedPurchase> purchases = orderCheckoutDomainService.refundEventPurchases(eventId);
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

    public void removeItemFromOrder(String token, UUID itemId) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        ticketReservationDomainService.removeItemFromOrder(sessionId, memberId, itemId);
    }

    public void updateGAQuantity(String token, UUID zoneId, int newQuantity) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        ticketReservationDomainService.updateGAQuantity(sessionId, memberId, zoneId, newQuantity);
    }

    public void cancelOrder(String token) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        ticketReservationDomainService.cancelOrder(sessionId, memberId);
    }

    public ActiveOrderDto getActiveOrder(String token) {
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        ActiveOrder order = ticketReservationDomainService.getActiveOrder(sessionId, memberId);
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

    public List<PurchaseRecordDTO> getPurchaseHistory(String token) {
        validateToken(token);
        UUID memberId = sessionTokenService.extractMemberId(token);
        if (memberId == null) {
            throw new SecurityException("User must be logged in to view purchase history");
        }
        List<CompletedPurchase> purchases = orderCheckoutDomainService.getPurchaseHistory(memberId);
        List<PurchaseRecordDTO> result = new java.util.ArrayList<>();
        for (CompletedPurchase p : purchases) {
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

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 10_000)
    public void expireOrders() {
        if (orderTimeDomainService != null) {
            int expiredCount = orderTimeDomainService.expireOrders();
            if (expiredCount > 0) {
                log.info("Expired {} orders", expiredCount);
            }
        }
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
