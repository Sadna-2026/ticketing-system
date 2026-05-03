package com.ticketing.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.IActiveOrderRepository;


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

        if (!event.isPurchasable()) {
            throw new IllegalStateException("Event is not available for purchase");
        }

        ActiveOrder order = new ActiveOrder(UUID.randomUUID(), sessionId, memberId, eventId, systemClock.now());
        activeOrderRepository.save(order);

        log.info("Order created: orderId={}, sessionId={}, eventId={}", order.getId(), sessionId, eventId);
        return order.getId();
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
}