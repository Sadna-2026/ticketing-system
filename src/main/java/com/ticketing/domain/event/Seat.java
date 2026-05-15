package com.ticketing.domain.event;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
 */
public class Seat {

    private final UUID id;
    private final String row;
    private final String seatNumber;
    private final AtomicReference<SeatStatus> status = new AtomicReference<>(SeatStatus.AVAILABLE);

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
