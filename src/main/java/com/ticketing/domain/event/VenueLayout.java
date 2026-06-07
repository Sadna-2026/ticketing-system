package com.ticketing.domain.event;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A visual, grid-based description of a hall: a {@code rows × cols} grid where each
 * occupied {@link LayoutCell} is an exact seat, a general-admission area, a blocked
 * area, a stage, or a decorative object.
 *
 * <p>Immutable value object owned by an {@link Event}. It carries geometry only —
 * referential integrity against the event's real {@link InventoryZone}s/{@link Seat}s
 * is enforced by the application layer when the layout is attached.
 */
public final class VenueLayout {

    private final int rows;
    private final int cols;
    private final List<LayoutCell> cells;

    public VenueLayout(int rows, int cols, List<LayoutCell> cells) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("Layout must have positive rows and cols");
        }
        if (cells == null) {
            throw new IllegalArgumentException("cells is required (may be empty)");
        }
        Set<Long> seen = new HashSet<>();
        for (LayoutCell cell : cells) {
            if (cell.getRow() >= rows || cell.getCol() >= cols) {
                throw new IllegalArgumentException(
                        "Cell (" + cell.getRow() + "," + cell.getCol() + ") is outside the "
                                + rows + "x" + cols + " grid");
            }
            long key = (long) cell.getRow() * cols + cell.getCol();
            if (!seen.add(key)) {
                throw new IllegalArgumentException(
                        "Two cells occupy the same position (" + cell.getRow() + "," + cell.getCol() + ")");
            }
        }
        this.rows = rows;
        this.cols = cols;
        this.cells = List.copyOf(cells);
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public List<LayoutCell> getCells() { return cells; }

    public Optional<LayoutCell> cellAt(int row, int col) {
        return cells.stream().filter(c -> c.getRow() == row && c.getCol() == col).findFirst();
    }

    public List<LayoutCell> cellsOfType(LayoutCellType type) {
        List<LayoutCell> out = new ArrayList<>();
        for (LayoutCell c : cells) {
            if (c.getType() == type) out.add(c);
        }
        return out;
    }

    /** Distinct zone ids referenced by sellable (seat / GA) cells. */
    public Set<java.util.UUID> referencedZoneIds() {
        Set<java.util.UUID> ids = new HashSet<>();
        for (LayoutCell c : cells) {
            if (c.getZoneId() != null) ids.add(c.getZoneId());
        }
        return ids;
    }

    /** Distinct seat ids referenced by SEAT cells. */
    public Set<java.util.UUID> referencedSeatIds() {
        Set<java.util.UUID> ids = new HashSet<>();
        for (LayoutCell c : cells) {
            if (c.getSeatId() != null) ids.add(c.getSeatId());
        }
        return ids;
    }

    public boolean hasSellableCell() {
        return cells.stream().anyMatch(LayoutCell::isSellable);
    }
}
