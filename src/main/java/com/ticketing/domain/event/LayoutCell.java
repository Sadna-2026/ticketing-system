package com.ticketing.domain.event;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * One cell in a visual {@link VenueLayout} grid.
 *
 * <p>Geometry (row/col) and presentation (label) live here, keeping the existing
 * inventory model ({@link InventoryZone}/{@link Seat}) free of layout concerns.
 *
 * <ul>
 *   <li>{@code SEAT} — sellable; {@code zoneId} = the assigned-seating zone, {@code seatId} = the seat.</li>
 *   <li>{@code GENERAL_ADMISSION} — sellable; {@code zoneId} = the GA zone, {@code seatId} null.</li>
 *   <li>{@code BLOCKED}/{@code STAGE}/{@code OBJECT} — non-sellable; {@code zoneId}/{@code seatId} null,
 *       {@code label} optional (e.g. "Main Stage", "Entrance", "VIP").</li>
 * </ul>
 *
 * <p>Mapped as an {@code @Embeddable} (no nested collections) inside the
 * {@link VenueLayout} {@code @ElementCollection}. {@code final} was removed from the
 * fields so JPA can set them; the public API and static factories are unchanged.
 */
@Embeddable
public class LayoutCell {

    @Column(name = "cell_row")
    private int row;
    @Column(name = "cell_col")
    private int col;
    @Enumerated(EnumType.STRING)
    @Column(name = "cell_type")
    private LayoutCellType type;
    @Column(name = "cell_label")
    private String label;
    @Column(name = "cell_zone_id")
    private UUID zoneId;
    @Column(name = "cell_seat_id")
    private UUID seatId;

    // Required by JPA; do not use directly.
    protected LayoutCell() {
    }

    public LayoutCell(int row, int col, LayoutCellType type, String label, UUID zoneId, UUID seatId) {
        if (row < 0 || col < 0) {
            throw new IllegalArgumentException("Cell row/col must be non-negative");
        }
        if (type == null) {
            throw new IllegalArgumentException("Cell type is required");
        }
        // zoneId/seatId are optional: a sellable cell may either link to a concrete
        // InventoryZone/Seat (validated by the application layer) or be purely visual
        // (the designer places shapes before/independently of the inventory ids).
        this.row = row;
        this.col = col;
        this.type = type;
        this.label = label;
        this.zoneId = zoneId;
        this.seatId = seatId;
    }

    public static LayoutCell seat(int row, int col, UUID zoneId, UUID seatId) {
        return new LayoutCell(row, col, LayoutCellType.SEAT, null, zoneId, seatId);
    }

    public static LayoutCell ga(int row, int col, UUID zoneId, String label) {
        return new LayoutCell(row, col, LayoutCellType.GENERAL_ADMISSION, label, zoneId, null);
    }

    public static LayoutCell blocked(int row, int col) {
        return new LayoutCell(row, col, LayoutCellType.BLOCKED, null, null, null);
    }

    public static LayoutCell stage(int row, int col, String label) {
        return new LayoutCell(row, col, LayoutCellType.STAGE, label, null, null);
    }

    public static LayoutCell object(int row, int col, String label) {
        return new LayoutCell(row, col, LayoutCellType.OBJECT, label, null, null);
    }

    public int getRow() { return row; }
    public int getCol() { return col; }
    public LayoutCellType getType() { return type; }
    public String getLabel() { return label; }
    public UUID getZoneId() { return zoneId; }
    public UUID getSeatId() { return seatId; }

    public boolean isSellable() {
        return type == LayoutCellType.SEAT || type == LayoutCellType.GENERAL_ADMISSION;
    }
}
