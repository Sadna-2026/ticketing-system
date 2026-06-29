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
    private final BigDecimal subtotal;
    private final BigDecimal totalPrice;
    private final String eventName;
    private final boolean lotteryWin;
    private final Instant purchaseWindowDeadline;
    private final String couponCode;

    public ActiveOrderDto(UUID id, UUID sessionId, UUID memberId, UUID eventId,
                          Instant createdAt, String status, List<OrderItemDto> items,
                          BigDecimal subtotal, BigDecimal totalPrice, String couponCode) {
        this(id, sessionId, memberId, eventId, createdAt, status, items, subtotal, totalPrice, null, false, null, couponCode);
    }

    public ActiveOrderDto(UUID id, UUID sessionId, UUID memberId, UUID eventId,
                          Instant createdAt, String status, List<OrderItemDto> items,
                          BigDecimal subtotal, BigDecimal totalPrice, String eventName, String couponCode) {
        this(id, sessionId, memberId, eventId, createdAt, status, items, subtotal, totalPrice, eventName, false, null, couponCode);
    }

    public ActiveOrderDto(UUID id, UUID sessionId, UUID memberId, UUID eventId,
                          Instant createdAt, String status, List<OrderItemDto> items,
                          BigDecimal subtotal, BigDecimal totalPrice, String eventName, boolean lotteryWin, String couponCode) {
        this(id, sessionId, memberId, eventId, createdAt, status, items, subtotal, totalPrice, eventName, lotteryWin, null, couponCode);
    }

    public ActiveOrderDto(UUID id, UUID sessionId, UUID memberId, UUID eventId,
                          Instant createdAt, String status, List<OrderItemDto> items,
                          BigDecimal subtotal, BigDecimal totalPrice, String eventName, boolean lotteryWin,
                          Instant purchaseWindowDeadline, String couponCode) {
        this.id = id;
        this.sessionId = sessionId;
        this.memberId = memberId;
        this.eventId = eventId;
        this.createdAt = createdAt;
        this.status = status;
        this.items = items;
        this.subtotal = subtotal;
        this.totalPrice = totalPrice;
        this.eventName = eventName;
        this.lotteryWin = lotteryWin;
        this.purchaseWindowDeadline = purchaseWindowDeadline;
        this.couponCode = couponCode;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public UUID getMemberId() { return memberId; }
    public UUID getEventId() { return eventId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public List<OrderItemDto> getItems() { return items; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public String getEventName() { return eventName; }
    public boolean isLotteryWin() { return lotteryWin; }
    public Instant getPurchaseWindowDeadline() { return purchaseWindowDeadline; }
    public String getCouponCode() { return couponCode; }
}

