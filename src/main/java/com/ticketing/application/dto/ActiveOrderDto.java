package com.ticketing.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Object for an active order (cart).
 */
public final class ActiveOrderDto {

    private final UUID id;
    private final UUID sessionId;
    private final UUID memberId;
    private final UUID eventId;
    private final Instant createdAt;
    private final String status;
    private final List<OrderItemDto> items;
    private final BigDecimal totalPrice;
    private final String eventName;

    public ActiveOrderDto(UUID id, UUID sessionId, UUID memberId, UUID eventId,
                          Instant createdAt, String status, List<OrderItemDto> items,
                          BigDecimal totalPrice) {
        this(id, sessionId, memberId, eventId, createdAt, status, items, totalPrice, null);
    }

    public ActiveOrderDto(UUID id, UUID sessionId, UUID memberId, UUID eventId,
                          Instant createdAt, String status, List<OrderItemDto> items,
                          BigDecimal totalPrice, String eventName) {
        this.id = id;
        this.sessionId = sessionId;
        this.memberId = memberId;
        this.eventId = eventId;
        this.createdAt = createdAt;
        this.status = status;
        this.items = items;
        this.totalPrice = totalPrice;
        this.eventName = eventName;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public UUID getMemberId() { return memberId; }
    public UUID getEventId() { return eventId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public List<OrderItemDto> getItems() { return items; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public String getEventName() { return eventName; }
}

