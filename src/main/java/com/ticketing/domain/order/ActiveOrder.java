package com.ticketing.domain.order;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ticketing.application.dto.OrderItemDto;

public class ActiveOrder{

    private final UUID id;
    private UUID sessionId;
    private UUID memberId;
    private final UUID eventId;
    private final Instant createdAt;
    private OrderStatus status;
    private final List<OrderItem> items;
    private int version;
     
    /**
     * Creates an ActiveOrder without a memberId (guest order).
     *
     * @param id the order's unique identifier
     * @param sessionId the session that owns this order
     * @param eventId the event this order is for
     * @param createdAt the creation timestamp (used for lock expiration)
     */
    public ActiveOrder(UUID id, UUID sessionId, UUID eventId, Instant createdAt) {
        this(id, sessionId, null, eventId, createdAt);
    }

    /**
     * Creates an ActiveOrder. memberId may be null for guest orders.
     *
     * @param id the order's unique identifier
     * @param sessionId the session that owns this order
     * @param memberId the member who owns this order (null for guests)
     * @param eventId the event this order is for
     * @param createdAt the creation timestamp (used for lock expiration)
     */
    public ActiveOrder(UUID id, UUID sessionId, UUID memberId, UUID eventId, Instant createdAt) {
        if (id == null) throw new IllegalArgumentException("Order ID is required");
        if (sessionId == null) throw new IllegalArgumentException("Session ID is required");
        if (eventId == null) throw new IllegalArgumentException("Event ID is required");
        if (createdAt == null) throw new IllegalArgumentException("CreatedAt is required");
        this.id = id;
        this.sessionId = sessionId;
        this.memberId = memberId;
        this.eventId = eventId;
        this.createdAt = createdAt;
        this.status = OrderStatus.ACTIVE;
        this.items = new ArrayList<>();
        this.version = 0;
    }

    public void updateSessionId(UUID newSessionId) {
        if (newSessionId == null) throw new IllegalArgumentException("Session ID is required");
        this.sessionId = newSessionId;
    }

    public void updateMemberId(UUID newMemberId) {
        if (newMemberId == null) throw new IllegalArgumentException("Member ID is required");
        this.memberId = newMemberId;
    }

    public UUID getId() { return id; }

    public boolean isActive() { return status == OrderStatus.ACTIVE; }
    public boolean isExpired() { return status == OrderStatus.EXPIRED; }
    public OrderStatus getStatus() { return status; }
    public UUID getEventId() { return eventId; }
    public UUID getMemberId() { return memberId; }

    public boolean isExpiredAt(Instant now, Duration lockDuration) {
        return now.isAfter(createdAt.plus(lockDuration));
    }

    public BigDecimal getTotalPrice() {
        return items.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Instant getCreatedAt() { return this.createdAt; }

    public List<OrderItemDto> getItemsDto() {
        List<OrderItemDto> rDtos = new ArrayList<>();
        for (OrderItem item : items)
            rDtos.add(item.getOrderItemDto());
        return rDtos;
    }

    /**
     * Adds an item to the order. Only on ACTIVE orders.
     */
    public void addItem(OrderItem item) {
        validateActive();
        if (item == null) throw new IllegalArgumentException("OrderItem cannot be null");
        items.add(item);
    }

    private void validateActive() {
        if (!isActive()) {
            throw new IllegalStateException("Order is not active (status: " + status + ")");
        }
    }

    /**
     * Finds an order item by zone ID (for GA quantity updates).
     */
    public Optional<OrderItem> findItemByZoneId(UUID zoneId) {
        return items.stream()
                .filter(i -> i.getZoneId().equals(zoneId) && i.isGA())
                .findFirst();
    }

    public UUID getSessionId() { return sessionId; }
    public int getVersion() { return version; }
    public void incrementVersion() { this.version++; }
    public int getTotalTicketCount() {
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
    }

    public void expire() {
        if (status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot expire a completed order");
        }
        this.status = OrderStatus.EXPIRED;
    }

    public List<OrderItem> getItems() { return items; }

    public void removeItem(UUID itemId) {
        validateActive();
        boolean removed = items.removeIf(i -> i.getId().equals(itemId));
        if (!removed) throw new IllegalArgumentException("Item not found: " + itemId);
    }
    

    public void startCheckout() {
        validateActive();
        if (items.isEmpty()) throw new IllegalStateException("Cannot checkout an empty order");
        this.status = OrderStatus.CHECKOUT_IN_PROGRESS;
    }
    
    public void revertToActive() {
        if (status != OrderStatus.CHECKOUT_IN_PROGRESS) {
            throw new IllegalStateException("Can only revert from CHECKOUT_IN_PROGRESS");
        }
        this.status = OrderStatus.ACTIVE;
    }

    public void complete() {
        if (status != OrderStatus.CHECKOUT_IN_PROGRESS) {
            throw new IllegalStateException("Can only complete from CHECKOUT_IN_PROGRESS");
        }
        this.status = OrderStatus.COMPLETED;
    }

    public void cancel() {
        if (status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed order");
        }
        this.status = OrderStatus.CANCELLED;
    }
}
