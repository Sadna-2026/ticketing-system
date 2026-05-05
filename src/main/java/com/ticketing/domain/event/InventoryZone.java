package com.ticketing.domain.event;

import java.math.BigDecimal;
import java.util.*;

/**
 * Entity within the Event aggregate.
 *
 * Represents a pricing/seating zone within a venue. Can be either General Admission
 * (with a max capacity and available count) or Assigned Seating (with specific seats).
 *
 * Invariants:
 * - GA zones: availableCount cannot exceed maxCapacity.
 * - Assigned zones: seats are individually managed.
 * - Price must be non-negative.
 */
public class InventoryZone {

    private final UUID id;
    private String name;
    private final ZoneType type;
    private BigDecimal pricePerTicket;

    // GA fields
    private int maxCapacity;
    private int availableCount;
    private int lockedCount;
    private int soldCount;

    // Assigned seating fields
    private final List<Seat> seats;

    /**
     * Creates a General Admission zone.
     */
    public static InventoryZone createGA(UUID id, String name, BigDecimal pricePerTicket, int maxCapacity) {
        InventoryZone zone = new InventoryZone(id, name, ZoneType.GENERAL_ADMISSION, pricePerTicket);
        if (maxCapacity <= 0) throw new IllegalArgumentException("Max capacity must be positive");
        zone.maxCapacity = maxCapacity;
        zone.availableCount = maxCapacity;
        zone.lockedCount = 0;
        zone.soldCount = 0;
        return zone;
    }

    /**
     * Creates an Assigned Seating zone.
     */
    public static InventoryZone createAssigned(UUID id, String name, BigDecimal pricePerTicket) {
        return new InventoryZone(id, name, ZoneType.ASSIGNED_SEATING, pricePerTicket);
    }

    private InventoryZone(UUID id, String name, ZoneType type, BigDecimal pricePerTicket) {
        if (id == null) throw new IllegalArgumentException("Zone ID is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Zone name is required");
        if (type == null) throw new IllegalArgumentException("Zone type is required");
        if (pricePerTicket == null || pricePerTicket.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price must be non-negative");
        }
        this.id = id;
        this.name = name;
        this.type = type;
        this.pricePerTicket = pricePerTicket;
        this.seats = new ArrayList<>();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public ZoneType getType() { return type; }
    public BigDecimal getPricePerTicket() { return pricePerTicket; }
    public int getMaxCapacity() { return maxCapacity; }
    public int getAvailableCount() { return type == ZoneType.GENERAL_ADMISSION ? availableCount : Math.toIntExact(seats.stream().filter(Seat::isAvailable).count()); }
    public int getLockedCount() { return type == ZoneType.GENERAL_ADMISSION ? lockedCount : Math.toIntExact(seats.stream().filter(Seat::isLocked).count()); }
    public int getSoldCount() { return type == ZoneType.GENERAL_ADMISSION ? soldCount : Math.toIntExact(seats.stream().filter(Seat::isSold).count()); }
    public List<Seat> getSeats() { return Collections.unmodifiableList(seats); }
    public boolean isGA() { return type == ZoneType.GENERAL_ADMISSION; }
    public boolean isAssigned() { return type == ZoneType.ASSIGNED_SEATING; }

    public void setName(String name) { this.name = name; }
    public void setPricePerTicket(BigDecimal pricePerTicket) { this.pricePerTicket = pricePerTicket; }

    /**
     * Adds a seat to an Assigned Seating zone.
     */
    public void addSeat(Seat seat) {
        if (!isAssigned()) throw new IllegalStateException("Cannot add seats to a GA zone");
        if (seat == null) throw new IllegalArgumentException("Seat cannot be null");
        seats.add(seat);
    }

    // --- GA Operations ---

    /**
     * Locks a number of GA tickets.
     */
    public void lockGA(int quantity) {
        if (!isGA()) throw new IllegalStateException("lockGA only applies to GA zones");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (quantity > availableCount) {
            throw new IllegalStateException("Not enough available tickets. Requested: " + quantity + ", Available: " + availableCount);
        }
        availableCount -= quantity;
        lockedCount += quantity;
    }

    /**
     * Releases locked GA tickets back to available.
     */
    public void releaseGA(int quantity) {
        if (!isGA()) throw new IllegalStateException("releaseGA only applies to GA zones");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (quantity > lockedCount) {
            throw new IllegalStateException("Cannot release more tickets than are locked");
        }
        lockedCount -= quantity;
        availableCount += quantity;
    }

    /**
     * Sells locked GA tickets.
     */
    public void sellGA(int quantity) {
        if (!isGA()) throw new IllegalStateException("sellGA only applies to GA zones");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        if (quantity > lockedCount) {
            throw new IllegalStateException("Cannot sell more than locked tickets");
        }
        lockedCount -= quantity;
        soldCount += quantity;
    }

    // --- Assigned Seating Operations ---

    /**
     * Locks a specific seat in an Assigned zone.
     */
    public Seat lockSeat(UUID seatId) {
        if (!isAssigned()) throw new IllegalStateException("lockSeat only applies to assigned zones");
        Seat seat = findSeat(seatId);
        seat.lock();
        return seat;
    }

    /**
     * Releases a specific seat in an Assigned zone.
     */
    public void releaseSeat(UUID seatId) {
        if (!isAssigned()) throw new IllegalStateException("releaseSeat only applies to assigned zones");
        Seat seat = findSeat(seatId);
        seat.release();
    }

    /**
     * Sells a specific seat in an Assigned zone.
     */
    public void sellSeat(UUID seatId) {
        if (!isAssigned()) throw new IllegalStateException("sellSeat only applies to assigned zones");
        Seat seat = findSeat(seatId);
        seat.sell();
    }

    public Seat findSeat(UUID seatId) {
        return seats.stream()
                .filter(s -> s.getId().equals(seatId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId));
    }

    /**
     * Returns the total capacity of this zone.
     */
    public int getTotalCapacity() {
        return isGA() ? maxCapacity : seats.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InventoryZone that = (InventoryZone) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
