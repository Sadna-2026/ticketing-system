package com.ticketing.domain.event;

import java.math.BigDecimal;
import java.util.*;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

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
 *
 * <p>JPA mapping: mapped as an {@code @Entity} (it owns a seat collection). {@code seats}
 * is a {@code @OneToMany(cascade=ALL, orphanRemoval=true)} to {@link Seat} and is loaded
 * {@code LAZY} — an assigned zone can hold thousands of seats, so callers traverse them
 * only inside a transaction. {@code final} was removed from {@code id}/{@code type}/{@code seats}
 * so JPA can set them; the public API and static factories are unchanged.
 */
@Entity
@Table(name = "inventory_zones")
public class InventoryZone {

    @Id
    @Column(name = "id")
    private UUID id;
    @Column(name = "name")
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private ZoneType type;
    @Column(name = "price_per_ticket")
    private BigDecimal pricePerTicket;

    // GA fields
    @Column(name = "max_capacity")
    private int maxCapacity;
    @Column(name = "available_count")
    private int availableCount;
    @Column(name = "locked_count")
    private int lockedCount;
    @Column(name = "sold_count")
    private int soldCount;

    // Assigned seating fields
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private List<Seat> seats;

    // V3-11 (#269): optimistic-lock guard. GA counts (available/locked/sold) live on
    // THIS zone entity, not on the Event ROOT, so a concurrent GA sell would not bump
    // the Event's @Version. This per-zone version is bumped on flush whenever the zone's
    // own state changes, so two concurrent decrements of the last GA ticket conflict —
    // the second (stale) merge is rejected with an OptimisticLockException → no
    // double-sell. The in-memory path never reads this field (its CAS uses the Event
    // aggregate version), so memory-mode behaviour is unchanged.
    @Version
    @Column(name = "version")
    private int version;

    // Required by JPA; do not use directly.
    protected InventoryZone() {
        this.seats = new ArrayList<>();
    }

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

    /** Repository-internal (V3-11 #269): the JPA optimistic-lock version of this zone.
     *  Not meant for domain/service use. */
    public int getVersion() { return version; }

    /** Repository-internal (V3-11 #269): after a successful merge+flush, the JPA repo
     *  copies the post-flush versions (this zone and its seats, matched by id) from the
     *  managed copy back into this (still detached) instance, so a second save of the
     *  SAME aggregate in the SAME transaction is not mistaken for a concurrent edit. A
     *  genuinely concurrent writer holds a different snapshot that never receives this
     *  sync, so it still conflicts. */
    public void syncVersionFrom(InventoryZone managed) {
        if (managed == null) {
            return;
        }
        this.version = managed.version;
        for (Seat seat : this.seats) {
            managed.seats.stream()
                    .filter(m -> m.getId().equals(seat.getId()))
                    .findFirst()
                    .ifPresent(seat::syncVersionFrom);
        }
    }

    public void setName(String name) { this.name = name; }

    /**
     * Changes the price. Rejects if any ticket in this zone is locked or sold —
     * we can't mutate the price someone is mid-purchase or has paid for.
     */
    public void setPricePerTicket(BigDecimal pricePerTicket) {
        if (pricePerTicket == null || pricePerTicket.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price must be non-negative");
        }
        if (getLockedCount() > 0 || getSoldCount() > 0) {
            throw new IllegalStateException(
                    "Cannot change price while tickets are locked or sold");
        }
        this.pricePerTicket = pricePerTicket;
    }

    /**
     * GA only. Adds capacity; new tickets become immediately available.
     */
    public void increaseCapacity(int delta) {
        if (!isGA()) throw new IllegalStateException("increaseCapacity only applies to GA zones");
        if (delta <= 0) throw new IllegalArgumentException("delta must be positive");
        this.maxCapacity += delta;
        this.availableCount += delta;
    }

    /**
     * GA only. Reduces capacity by removing available tickets.
     * Rejects if there aren't enough free tickets to remove without touching
     * locked or sold state.
     */
    public void decreaseCapacity(int delta) {
        if (!isGA()) throw new IllegalStateException("decreaseCapacity only applies to GA zones");
        if (delta <= 0) throw new IllegalArgumentException("delta must be positive");
        if (availableCount < delta) {
            throw new IllegalStateException(
                    "Cannot remove " + delta + " tickets — only " + availableCount + " are free");
        }
        this.maxCapacity -= delta;
        this.availableCount -= delta;
    }

    /**
     * Assigned only. Removes a seat — only if it's currently AVAILABLE.
     */
    public void removeSeat(UUID seatId) {
        if (!isAssigned()) throw new IllegalStateException("removeSeat only applies to assigned zones");
        Seat seat = findSeat(seatId);
        if (!seat.isAvailable()) {
            throw new IllegalStateException(
                    "Cannot remove seat " + seat.getRow() + "-" + seat.getSeatNumber()
                    + " in status: " + seat.getStatus());
        }
        seats.removeIf(s -> s.getId().equals(seatId));
    }

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
