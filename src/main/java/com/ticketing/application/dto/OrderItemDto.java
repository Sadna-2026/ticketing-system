package com.ticketing.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Data Transfer Object for an item in an active order.
 */
public final class OrderItemDto {

    private final UUID id;
    private final UUID zoneId;
    private final UUID seatId;
    private final int quantity;
    private final BigDecimal pricePerTicket;
    private final BigDecimal totalPrice;
    private final boolean assignedSeat;

    public OrderItemDto(UUID id, UUID zoneId, UUID seatId, int quantity,
                        BigDecimal pricePerTicket, BigDecimal totalPrice, boolean assignedSeat) {
        this.id = id;
        this.zoneId = zoneId;
        this.seatId = seatId;
        this.quantity = quantity;
        this.pricePerTicket = pricePerTicket;
        this.totalPrice = totalPrice;
        this.assignedSeat = assignedSeat;
    }

    public UUID getId() { return id; }
    public UUID getZoneId() { return zoneId; }
    public UUID getSeatId() { return seatId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getPricePerTicket() { return pricePerTicket; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public boolean isAssignedSeat() { return assignedSeat; }
}

