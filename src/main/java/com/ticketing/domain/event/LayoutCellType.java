package com.ticketing.domain.event;

/**
 * The kind of thing a single cell in a visual venue layout represents.
 *
 * <p>{@code SEAT} and {@code GENERAL_ADMISSION} cells are <em>sellable</em> and link
 * back to an {@link InventoryZone} (and, for seats, a specific {@link Seat}). The other
 * types are non-sellable authoring decorations that only describe how the hall looks,
 * so the buyer map can show the real venue shape rather than a generic grid.
 */
public enum LayoutCellType {
    SEAT,
    GENERAL_ADMISSION,
    BLOCKED,
    STAGE,
    OBJECT
}
