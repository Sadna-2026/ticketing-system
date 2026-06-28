package com.ticketing.domain.order;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.ticketing.application.dto.OrderItemDto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entity within the ActiveOrder aggregate.
 *
 * Represents a single item in an active order.
 * For assigned seating: references a specific seatId.
 * For GA: seatId is null, and quantity represents how many tickets from that zone.
 *
 * <p>JPA mapping: field access. A nullable {@code seat_id} column distinguishes the two
 * ticket modes exactly as the in-memory model does — {@link #isAssignedSeat()} stays
 * {@code seatId != null} and {@link #isGA()} stays {@code seatId == null}, so no extra
 * discriminator column is needed. {@code final} was removed from {@code id}/{@code zoneId}/
 * {@code seatId}/{@code pricePerTicket} so JPA can set them; the factory methods and all
 * behaviour are unchanged.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @Column(name = "id")
    private UUID id;
    // #510: optimistic-lock guard. updateQuantity() can be called concurrently when the same
    // GA item exists in two presenter sessions; without its own version, the slower writer would
    // silently overwrite. The ActiveOrder root's @Version catches whole-order concurrent edits
    // but not in-place quantity bumps to a single OrderItem row.
    @Version
    @Column(name = "version")
    private int version;
    @Column(name = "zone_id")
    private UUID zoneId;
    @Column(name = "seat_id")
    private UUID seatId;
    @Column(name = "quantity")
    private int quantity;
    @Column(name = "price_per_ticket")
    private BigDecimal pricePerTicket;

    // Required by JPA; do not use directly.
    protected OrderItem() {
    }

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

    public OrderItemDto getOrderItemDto(){
        return new OrderItemDto(id, zoneId, seatId, quantity, pricePerTicket, getTotalPrice(), isAssignedSeat());
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

