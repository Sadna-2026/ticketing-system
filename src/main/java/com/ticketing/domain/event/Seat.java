package com.ticketing.domain.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Entity within the InventoryZone (which is within the Event aggregate).
 *
 * Represents an individual seat in an assigned seating zone.
 *
 * Invariants:
 * - A seat can only be locked if AVAILABLE.
 * - A seat can only be sold if LOCKED.
 * - A released seat returns to AVAILABLE.
 */
public class Seat {

    private final UUID id;
    private final String row;
    private final String seatNumber;
    private SeatStatus status;

    public Seat(UUID id, String row, String seatNumber) {
        if (id == null) throw new IllegalArgumentException("Seat ID is required");
        if (row == null || row.isBlank()) throw new IllegalArgumentException("Row is required");
        if (seatNumber == null || seatNumber.isBlank()) throw new IllegalArgumentException("Seat number is required");
        this.id = id;
        this.row = row;
        this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE;
    }

    public UUID getId() { return id; }
    public String getRow() { return row; }
    public String getSeatNumber() { return seatNumber; }
    public SeatStatus getStatus() { return status; }

    public boolean isAvailable() { return status == SeatStatus.AVAILABLE; }
    public boolean isLocked() { return status == SeatStatus.LOCKED; }
    public boolean isSold() { return status == SeatStatus.SOLD; }

    /**
     * Locks this seat (reserves it for an active order).
     */
    public void lock() {
        if (status != SeatStatus.AVAILABLE) {
            throw new IllegalStateException("Seat " + row + "-" + seatNumber + " is not available (status: " + status + ")");
        }
        this.status = SeatStatus.LOCKED;
    }

    /**
     * Marks this seat as sold.
     */
    public void sell() {
        if (status != SeatStatus.LOCKED) {
            throw new IllegalStateException("Seat " + row + "-" + seatNumber + " is not locked (status: " + status + ")");
        }
        this.status = SeatStatus.SOLD;
    }

    /**
     * Releases this seat back to available.
     */
    public void release() {
        if (status == SeatStatus.AVAILABLE) {
            throw new IllegalStateException("Seat is already available");
        }
        this.status = SeatStatus.AVAILABLE;
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

