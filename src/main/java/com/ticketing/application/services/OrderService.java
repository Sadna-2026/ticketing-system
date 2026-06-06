package com.ticketing.application.services;

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
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.OrderCheckoutDomainService;
import com.ticketing.domain.order.TicketReservationDomainService;
import com.ticketing.domain.queue.IQueueRepository;
import com.ticketing.domain.queue.QueueConfig;
import com.ticketing.domain.queue.QueueEntry;
import com.ticketing.domain.queue.VirtualQueue;
import com.ticketing.domain.services.OrderTimeDomainService;

@org.springframework.stereotype.Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final ISessionTokenService sessionTokenService;
    private final TicketReservationDomainService ticketReservationDomainService;
    private final OrderCheckoutDomainService orderCheckoutDomainService;
    private final IQueueRepository queueRepository;
    private final IEventRepository eventRepository;
    private final ISystemClock systemClock;
    private final OrderTimeDomainService orderTimeDomainService;
    private final INotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired
    public OrderService(ISessionTokenService sessionTokenService,
            TicketReservationDomainService ticketReservationDomainService,
            OrderCheckoutDomainService orderCheckoutDomainService,
            IQueueRepository queueRepository,
            IEventRepository eventRepository,
            ISystemClock systemClock,
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
        this.queueRepository = queueRepository;
        this.eventRepository = eventRepository;
        this.systemClock = systemClock;
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

    // ── Virtual Queue methods ──────────────────────────────────────────

    public UUID createQueue(String token, UUID eventId, int threshold, int flowRate) {
        validateToken(token);

        if (eventRepository != null) {
            eventRepository.findById(eventId)
                    .orElseThrow(() -> {
                        log.warn("Event not found: eventId={}", eventId);
                        return new IllegalArgumentException("Event not found: " + eventId);
                    });
        }

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

    public void userLeft(UUID eventId) {
        VirtualQueue queue = queueRepository.findByEventId(eventId).orElse(null);
        if (queue != null) {
            queue.userLeft();
            saveQueue(queue);
        }
    }

    public void updateQueueConfig(String token, UUID eventId, int threshold, int flowRate) {
        validateToken(token);
        VirtualQueue queue = findQueueByEvent(eventId);
        queue.updateConfig(new QueueConfig(threshold, flowRate));
        saveQueue(queue);
        log.info("Queue config updated: eventId={}", eventId);
    }

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
    public void expireOrders() {
        if (orderTimeDomainService != null) {
            orderTimeDomainService.expireOrders();
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
}
