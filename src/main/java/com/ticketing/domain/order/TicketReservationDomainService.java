package com.ticketing.domain.order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.ISystemClock;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.exception.OptimisticLockException;

@org.springframework.stereotype.Service
public class TicketReservationDomainService {

    private static final Logger log = LoggerFactory.getLogger(TicketReservationDomainService.class);

    private final IOrderRepository orderRepository;
    private final IEventRepository eventRepository;
    private final ISystemClock systemClock;

    public TicketReservationDomainService(IOrderRepository orderRepository,
                                          IEventRepository eventRepository,
                                          ISystemClock systemClock) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.systemClock = systemClock;
    }

    public ActiveOrder findOrCreateActiveOrder(UUID sessionId, UUID memberId, UUID eventId) {
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

        order = new ActiveOrder(UUID.randomUUID(), sessionId, memberId, eventId, systemClock.now());
        saveOrder(order);
        log.info("Order created: orderId={}, sessionId={}, eventId={}", order.getId(), sessionId, eventId);
        return order;
    }

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
                saveEvent(event);
                saveOrder(order);
                return null;
            }
        }
        return order;
    }

    public UUID createOrder(UUID sessionId, UUID memberId, UUID eventId) {
        return findOrCreateActiveOrder(sessionId, memberId, eventId).getId();
    }

    public UUID addSeatToOrder(UUID sessionId, UUID memberId, UUID eventId, UUID zoneId, UUID seatId) {
        ActiveOrder order = findOrCreateActiveOrder(sessionId, memberId, eventId);
        validateOrderOwnership(sessionId, order);
        return addSelectionToOrder(sessionId, order,
                new SelectionRequest(order.getEventId(),
                        List.of(new SelectionRequest.SeatPick(zoneId, seatId)),
                        List.of())).get(0);
    }

    public UUID addGATicketsToOrder(UUID sessionId, UUID memberId, UUID eventId, UUID zoneId, int quantity) {
        ActiveOrder order = findOrCreateActiveOrder(sessionId, memberId, eventId);
        validateOrderOwnership(sessionId, order);
        return addSelectionToOrder(sessionId, order,
                new SelectionRequest(order.getEventId(),
                        List.of(),
                        List.of(new SelectionRequest.GAPick(zoneId, quantity)))).get(0);
    }

    public List<UUID> addSelectionToOrder(UUID sessionId, ActiveOrder order, SelectionRequest request) {
        validateOrderOwnership(sessionId, order);
        if (!order.getEventId().equals(request.eventId())) {
            log.warn("Failed to add selection to order: selection event {} does not match order event {}", request.eventId(), order.getEventId());
            throw new IllegalArgumentException("Selection event does not match order event");
        }

        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);
        validateSelection(request, event);

        List<UUID> itemIds = new ArrayList<>();
        for (SelectionRequest.SeatPick pick : request.seats()) {
            itemIds.add(lockSeat(order, event, pick.zoneId(), pick.seatId()));
        }
        for (SelectionRequest.GAPick pick : request.gaQuantities()) {
            itemIds.add(lockGA(order, event, pick.zoneId(), pick.quantity()));
        }

        saveEvent(event);
        saveOrder(order);
        checkAndPublishSoldOut(event);
        return itemIds;
    }

    public void removeItemFromOrder(UUID sessionId, UUID memberId, UUID itemId) {
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
        saveEvent(event);

        order.removeItem(itemId);
        saveOrder(order);
        log.info("Item removed from order: orderId={}, itemId={}", order.getId(), itemId);
    }

    public void updateGAQuantity(UUID sessionId, UUID memberId, UUID zoneId, int newQuantity) {
        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order == null) throw new IllegalArgumentException("No active order found");
        validateOrderOwnership(sessionId, order);
        Event event = findEvent(order.getEventId());
        validateOrderNotExpired(order, event);

        updateGAQuantity(order, event, zoneId, newQuantity);
        saveEvent(event);

        saveOrder(order);
        log.info("GA quantity updated: orderId={}, zoneId={}", order.getId(), zoneId);
    }

    public void cancelOrder(UUID sessionId, UUID memberId) {
        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order == null) throw new IllegalArgumentException("No active order found");
        validateOrderOwnership(sessionId, order);
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed order");
        }

        Event event = findEvent(order.getEventId());
        releaseAllInventory(event, order);
        saveEvent(event);

        order.cancel();
        saveOrder(order);
        log.info("Order cancelled: orderId={}", order.getId());
    }

    public ActiveOrder getValidatedActiveOrder(UUID sessionId, UUID memberId) {
        ActiveOrder order = getActiveOrder(sessionId, memberId);
        if (order == null) throw new IllegalArgumentException("No active order found");
        validateOrderOwnership(sessionId, order);
        return order;
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

    // ── Inventory lock/release (pure domain logic) ──

    public UUID lockSeat(ActiveOrder order, Event event, UUID zoneId, UUID seatId) {
        InventoryZone zone = event.findZone(zoneId);
        zone.lockSeat(seatId);
        OrderItem item = OrderItem.forSeat(UUID.randomUUID(), zoneId, seatId, zone.getPricePerTicket());
        order.addItem(item);
        log.info("Seat added to order: orderId={}, seatId={}", order.getId(), seatId);
        return item.getId();
    }

    public UUID lockGA(ActiveOrder order, Event event, UUID zoneId, int quantity) {
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

    public void updateGAQuantity(ActiveOrder order, Event event, UUID zoneId, int newQuantity) {
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

    public void releaseInventoryForItem(Event event, OrderItem item) {
        InventoryZone zone = event.findZone(item.getZoneId());
        if (item.isAssignedSeat()) {
            zone.releaseSeat(item.getSeatId());
        } else {
            zone.releaseGA(item.getQuantity());
        }
    }

    public void releaseAllInventory(Event event, ActiveOrder order) {
        for (OrderItem item : order.getItems()) {
            try {
                releaseInventoryForItem(event, item);
            } catch (Exception e) {
                log.error("Failed to release inventory for item: {}", item.getId(), e);
            }
        }
    }

    public void sellAllInventory(Event event, ActiveOrder order) {
        for (OrderItem item : order.getItems()) {
            InventoryZone zone = event.findZone(item.getZoneId());
            if (item.isAssignedSeat()) {
                zone.sellSeat(item.getSeatId());
            } else {
                zone.sellGA(item.getQuantity());
            }
        }
    }

    // ── Selection validation ──

    private void validateSelection(SelectionRequest request, Event event) {
        if (request == null) throw new IllegalArgumentException("request is required");
        if (request.isEmpty()) {
            throw new IllegalArgumentException("selection must include at least one seat or quantity");
        }

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new IllegalStateException("Event is not selectable in status: " + event.getStatus());
        }

        for (SelectionRequest.SeatPick pick : request.seats()) {
            InventoryZone zone = findZone(event, pick.zoneId());
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
        for (SelectionRequest.GAPick pick : request.gaQuantities()) {
            totalsByZone.merge(pick.zoneId(), pick.quantity(), Integer::sum);
        }
        for (var entry : totalsByZone.entrySet()) {
            InventoryZone zone = findZone(event, entry.getKey());
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

    private static InventoryZone findZone(Event event, UUID zoneId) {
        try {
            return event.findZone(zoneId);
        } catch (IllegalArgumentException notFound) {
            throw new IllegalArgumentException(
                    "Zone " + zoneId + " is not part of event " + event.getId());
        }
    }

    // ── Private helpers ──

    public Event findEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> {
                    log.warn("Event not found: eventId={}", eventId);
                    return new IllegalArgumentException("Event not found: " + eventId);
                });
    }

    public void saveEvent(Event event) {
        try {
            eventRepository.save(event);
        } catch (OptimisticLockException ex) {
            log.warn("Event save conflict during order flow: eventId={}", event.getId());
            throw new IllegalStateException("Event inventory changed concurrently. Please retry.", ex);
        }
    }

    public void saveOrder(ActiveOrder order) {
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

    public void checkAndPublishSoldOut(Event event) {
        if (!event.hasAvailableTickets() && event.isPublished()) {
            event.markSoldOut();
            saveEvent(event);
        }
    }
}
