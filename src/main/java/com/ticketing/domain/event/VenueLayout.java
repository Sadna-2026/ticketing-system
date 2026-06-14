package com.ticketing.domain.event;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;

/**
 * A visual, grid-based description of a hall: a {@code rows × cols} grid where each
 * occupied {@link LayoutCell} is an exact seat, a general-admission area, a blocked
 * area, a stage, or a decorative object.
 *
 * <p>Immutable value object owned by an {@link Event}. It carries geometry only —
 * referential integrity against the event's real {@link InventoryZone}s/{@link Seat}s
 * is enforced by the application layer when the layout is attached.
 *
 * <p>Mapped as an {@code @Embeddable} embedded (nullable) in {@link Event}; the
 * cells are an {@code @ElementCollection} of the {@link LayoutCell} embeddable.
 * {@code final} was removed from the fields so JPA can set them via field access;
 * the public API is unchanged.
 */
@Embeddable
public class VenueLayout {

    // Boxed so a fully-null embedded VenueLayout (an event with no layout) reloads as a
    // null embeddable rather than failing to assign null to a primitive. The public
    // getRows()/getCols() still return int (auto-unboxed).
    @Column(name = "layout_rows")
    private Integer rows;
    @Column(name = "layout_cols")
    private Integer cols;
    @ElementCollection
    @CollectionTable(
            name = "venue_layout_cells",
            joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "cell_index")
    private List<LayoutCell> cells;

    // Required by JPA; do not use directly.
    protected VenueLayout() {
    }

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
