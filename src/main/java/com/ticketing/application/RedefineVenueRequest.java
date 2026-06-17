package com.ticketing.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Redefines a DRAFT event's hall from a single painted grid: the zones, their
 * seats/capacity, the section→zone venue map, and the visual layout are all rebuilt.
 * This is meant for MAP_DEFINITION managers who cannot change event metadata.
 */
public record RedefineVenueRequest(
        UUID eventId,
        int rows,
        int cols,
        List<CreateEventRequest.ZoneSpec> zones,
        Map<String, String> sectionToZoneName,
        List<DefineVenueRequest.CellSpec> cells
) {
    public RedefineVenueRequest {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
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
}
