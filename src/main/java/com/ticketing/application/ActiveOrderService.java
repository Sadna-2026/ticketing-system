package com.ticketing.application;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.IActiveOrderRepository;
import com.ticketing.domain.order.OrderItem;


public class ActiveOrderService {

    private static final Logger log = LoggerFactory.getLogger(ActiveOrderService.class);

    private final ISessionTokenService sessionTokenService;
    private final IActiveOrderRepository activeOrderRepository;
    private final IEventRepository eventRepository;
    private final ISystemClock systemClock;

    public ActiveOrderService(IActiveOrderRepository activeOrderRepository,
                        ISessionTokenService sessionTokenService,
                        IEventRepository eventRepository,
                        ISystemClock systemClock) {
        this.activeOrderRepository = activeOrderRepository;
        this.sessionTokenService = sessionTokenService;
        this.eventRepository = eventRepository;
        this.systemClock = systemClock;
    }

    /**
     * Creates an active order for the given session and event.
     * Can be called by guests (no token) or members (with token).
     *
     * @param token JWT token (null for guests)
     * @param eventId the event to order tickets for
     * @return the new order's UUID
     */
    public UUID createOrder(String token, UUID eventId) {

        // validate token
        validateToken(token);
        UUID sessionId = sessionTokenService.extractSessionId(token);
        UUID memberId = sessionTokenService.extractMemberId(token);     // null for guests

        // Enforce max 1 active order per session
        activeOrderRepository.findActiveBySessionId(sessionId).ifPresent(existing -> {
            throw new IllegalStateException("Session already has an active order");
        });

        Event event = findEvent(eventId);

        if (!event.isPublished()) {
            throw new IllegalStateException("Event is not available for purchase");
        }

        ActiveOrder order = new ActiveOrder(UUID.randomUUID(), sessionId, memberId, eventId, systemClock.now());
        activeOrderRepository.save(order);

        log.info("Order created: orderId={}, sessionId={}, eventId={}", order.getId(), sessionId, eventId);
        return order.getId();
    }

    /**
     * Adds a specific assigned seat to the active order.
     */
    public UUID addSeatToOrder(String token, UUID orderId, UUID zoneId, UUID seatId) {
        // validate token
        validateToken(token);

        log.info("Adding seat to order: orderId={}, zoneId={}, seatId={}", orderId, zoneId, seatId);

        ActiveOrder order = findActiveOrder(orderId);
        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);

        InventoryZone zone = event.findZone(zoneId);
        if (!zone.isAssigned()) {
            throw new IllegalStateException("Zone is not an assigned seating zone");
        }

        zone.lockSeat(seatId);
        eventRepository.save(event);

        OrderItem item = OrderItem.forSeat(UUID.randomUUID(), zoneId, seatId, zone.getPricePerTicket());
        order.addItem(item);
        activeOrderRepository.save(order);

        checkAndPublishSoldOut(event);

        log.info("Seat added to order: orderId={}, seatId={}", orderId, seatId);
        return item.getId();
    }

    
     /* Adds GA tickets from a zone to the active order.*/
    public UUID addGATicketsToOrder(String token, UUID orderId, UUID zoneId, int quantity) {
        // validate token
        validateToken(token);
        log.info("Adding GA tickets: orderId={}, zoneId={}, quantity={}", orderId, zoneId, quantity);

        ActiveOrder order = findActiveOrder(orderId);
        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);

        InventoryZone zone = event.findZone(zoneId);
        if (!zone.isGA()) {
            throw new IllegalStateException("Zone is not a General Admission zone");
        }

        Optional<OrderItem> existingItem = order.findItemByZoneId(zoneId);
        UUID itemId;
        if (existingItem.isPresent()) {
            int additionalQuantity = quantity;
            zone.lockGA(additionalQuantity);
            eventRepository.save(event);
            int oldQuantity = existingItem.get().getQuantity();
            existingItem.get().updateQuantity(oldQuantity + additionalQuantity);
            itemId = existingItem.get().getId();
        } else {
            zone.lockGA(quantity);
            eventRepository.save(event);
            OrderItem item = OrderItem.forGA(UUID.randomUUID(), zoneId, quantity, zone.getPricePerTicket());
            order.addItem(item);
            itemId = item.getId();
        }

        activeOrderRepository.save(order);
        checkAndPublishSoldOut(event);

        log.info("GA tickets added: orderId={}, zoneId={}, quantity={}", orderId, zoneId, quantity);
        return itemId;
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
        ActiveOrder order = activeOrderRepository.findById(orderId)
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

    private void checkAndPublishSoldOut(Event event) {
        if (!event.hasAvailableTickets() && event.isPublished()) {
            event.markSoldOut();
            eventRepository.save(event);
            // TODO: send event sold out messages to all listeners
        }
    }


}