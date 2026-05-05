package com.ticketing.domain.order;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity within the ActiveOrder aggregate.
 *
 * Represents a single item in an active order.
 * For assigned seating: references a specific seatId.
 * For GA: seatId is null, and quantity represents how many tickets from that zone.
 */
public class OrderItem {

    private final UUID id;
    private final UUID zoneId;
    private final UUID seatId;
    private int quantity;
    private final BigDecimal pricePerTicket;

    /**
     * Creates an order item for an assigned seat.
     */
    public static OrderItem forSeat(UUID id, UUID zoneId, UUID seatId, BigDecimal pricePerTicket) {
        if (seatId == null) throw new IllegalArgumentException("Seat ID required for assigned seating");
        return new OrderItem(id, zoneId, seatId, 1, pricePerTicket);
    }

    /**
     * Creates an order item for GA tickets.
     */
    public static OrderItem forGA(UUID id, UUID zoneId, int quantity, BigDecimal pricePerTicket) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        return new OrderItem(id, zoneId, null, quantity, pricePerTicket);
    }

    private OrderItem(UUID id, UUID zoneId, UUID seatId, int quantity, BigDecimal pricePerTicket) {
        if (id == null) throw new IllegalArgumentException("OrderItem ID is required");
        if (zoneId == null) throw new IllegalArgumentException("Zone ID is required");
        if (pricePerTicket == null || pricePerTicket.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price must be non-negative");
        }
        this.id = id;
        this.zoneId = zoneId;
        this.seatId = seatId;
        this.quantity = quantity;
        this.pricePerTicket = pricePerTicket;
    }

    public UUID getId() { return id; }
    public UUID getZoneId() { return zoneId; }
    public UUID getSeatId() { return seatId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getPricePerTicket() { return pricePerTicket; }

    public boolean isAssignedSeat() { return seatId != null; }
    public boolean isGA() { return seatId == null; }

    /**
     * Returns the total cost for this item.
     */
    public BigDecimal getTotalPrice() {
        return pricePerTicket.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Updates the quantity for a GA order item.
     */
    public void updateQuantity(int newQuantity) {
        if (isAssignedSeat()) throw new IllegalStateException("Cannot update quantity for assigned seats");
        if (newQuantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        this.quantity = newQuantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderItem orderItem = (OrderItem) o;
        return Objects.equals(id, orderItem.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

