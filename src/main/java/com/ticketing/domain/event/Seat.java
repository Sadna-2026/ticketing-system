package com.ticketing.domain.event;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Entity within the InventoryZone (which is within the Event aggregate).
 *
 * Represents an individual seat in an assigned seating zone.
 *
 * Invariants:
 * - A seat can only be locked if AVAILABLE.
 * - A seat can only be sold if LOCKED.
 * - A released seat returns to AVAILABLE.
 *
 * Seat status transitions use CAS so locking stays correct when the same aggregate
 * is mutated concurrently (e.g. in-memory repositories return shared instances).
 *
 * <p>JPA mapping: the class defaults to field access. {@code status} is an
 * {@link AtomicReference} (which JPA cannot map), so it is {@code @Transient}; the
 * persistent {@code status} column is driven by the property-access accessor pair
 * {@link #getStatusForJpa()}/{@link #setStatusForJpa(SeatStatus)}, keeping the CAS
 * logic in {@link #lock()}/{@link #sell()}/{@link #release()} intact. {@code final}
 * was removed from {@code id}/{@code row}/{@code seatNumber} so JPA can set them.
 */
@Entity
@Table(name = "seats")
public class Seat {

    @Id
    @Column(name = "id")
    private UUID id;
    @Column(name = "seat_row")
    private String row;
    @Column(name = "seat_number")
    private String seatNumber;
    @Transient
    private final AtomicReference<SeatStatus> status = new AtomicReference<>(SeatStatus.AVAILABLE);

    // Required by JPA; do not use directly.
    protected Seat() {
    }

    public Seat(UUID id, String row, String seatNumber) {
        if (id == null) throw new IllegalArgumentException("Seat ID is required");
        if (row == null || row.isBlank()) throw new IllegalArgumentException("Row is required");
        if (seatNumber == null || seatNumber.isBlank()) throw new IllegalArgumentException("Seat number is required");
        this.id = id;
        this.row = row;
        this.seatNumber = seatNumber;
    }

    public UUID getId() { return id; }
    public String getRow() { return row; }
    public String getSeatNumber() { return seatNumber; }
    public SeatStatus getStatus() { return status.get(); }

    // --- JPA status mapping (property access) ---
    // The AtomicReference itself is @Transient; this accessor pair persists/reloads the
    // current SeatStatus into the "status" column without exposing a public mutator that
    // could bypass the CAS guards.
    @Access(AccessType.PROPERTY)
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    protected SeatStatus getStatusForJpa() {
        return status.get();
    }

    protected void setStatusForJpa(SeatStatus s) {
        this.status.set(s == null ? SeatStatus.AVAILABLE : s);
    }

    public boolean isAvailable() { return status.get() == SeatStatus.AVAILABLE; }
    public boolean isLocked() { return status.get() == SeatStatus.LOCKED; }
    public boolean isSold() { return status.get() == SeatStatus.SOLD; }

    /**
     * Locks this seat (reserves it for an active order).
     */
    public void lock() {
        if (!status.compareAndSet(SeatStatus.AVAILABLE, SeatStatus.LOCKED)) {
            SeatStatus current = status.get();
            throw new IllegalStateException("Seat " + row + "-" + seatNumber + " is not available (status: " + current + ")");
        }
    }

    /**
     * Marks this seat as sold.
     */
    public void sell() {
        if (!status.compareAndSet(SeatStatus.LOCKED, SeatStatus.SOLD)) {
            SeatStatus current = status.get();
            throw new IllegalStateException("Seat " + row + "-" + seatNumber + " is not locked (status: " + current + ")");
        }
    }

    /**
     * Releases this seat back to available.
     */
    public void release() {
        while (true) {
            SeatStatus current = status.get();
            if (current == SeatStatus.AVAILABLE) {
                throw new IllegalStateException("Seat is already available");
            }
            if (status.compareAndSet(current, SeatStatus.AVAILABLE)) {
                return;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Seat seat = (Seat) o;
        return Objects.equals(id, seat.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
