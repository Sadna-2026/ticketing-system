package com.ticketing.domain.order;


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ActiveOrder{

    private final UUID id;
    private final UUID sessionId;
    private final UUID memberId;
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

    public UUID getId() { return id; }

    public boolean isActive() { return status == OrderStatus.ACTIVE; }
    public boolean isExpired() { return status == OrderStatus.EXPIRED; }
    public OrderStatus getStatus() { return status; }
    public UUID getEventId() { return eventId; }

    public boolean isExpiredAt(Instant now, Duration lockDuration) {
        return now.isAfter(createdAt.plus(lockDuration));
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
    
}
