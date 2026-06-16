package com.ticketing.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.LayoutCellType;
import com.ticketing.domain.event.LockTimerDuration;

/**
 * Defines (or redefines) a DRAFT event's hall from a single painted grid: the zones, their
 * seats/capacity, the section→zone venue map, and the visual layout are all derived from the
 * same {@link CellSpec} list, so the buyable inventory and the on-screen map can never drift.
 *
 * <p>When {@code eventId} is {@code null} a new event is created from the metadata fields;
 * otherwise the named DRAFT event is rebuilt in place (its old zones/seats are replaced).
 */
public record DefineVenueRequest(
        UUID eventId,
        String companyName,
        String name,
        String description,
        EventCategory category,
        EventSchedule schedule,
        LockTimerDuration lockTimerDuration,
        int rows,
        int cols,
        List<CreateEventRequest.ZoneSpec> zones,
        Map<String, String> sectionToZoneName,
        List<CellSpec> cells
) {

    public DefineVenueRequest {
        if (zones == null || zones.isEmpty()) {
            throw new IllegalArgumentException("at least one zone is required");
        }
        if (sectionToZoneName == null || sectionToZoneName.isEmpty()) {
            throw new IllegalArgumentException("sectionToZoneName must have at least one entry");
        }
        zones = List.copyOf(zones);
        sectionToZoneName = Map.copyOf(sectionToZoneName);
        cells = cells == null ? List.of() : List.copyOf(cells);
    }

    public boolean isCreate() {
        return eventId == null;
    }

    /**
     * One painted grid cell. {@code zoneName} is set for sellable cells; for {@code SEAT}
     * cells {@code seatRow}/{@code seatNumber} identify the seat within its assigned zone
     * (matching a {@link CreateEventRequest.SeatSpec}). Structural cells set only type/label.
     */
    public record CellSpec(
            int row,
            int col,
            LayoutCellType type,
            String label,
            String zoneName,
            String seatRow,
            String seatNumber
    ) {
        public CellSpec {
            if (type == null) {
                throw new IllegalArgumentException("cell type is required");
            }
            if (row < 0 || col < 0) {
                throw new IllegalArgumentException("cell row/col must be non-negative");
            }
        }
    }
}
