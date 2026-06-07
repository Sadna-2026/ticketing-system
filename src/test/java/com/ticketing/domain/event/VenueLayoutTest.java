package com.ticketing.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VenueLayout / LayoutCell value objects")
class VenueLayoutTest {

    @Test
    void GivenVisualOnlySellableCells_WhenConstruct_ThenAllowedAndSellable() {
        // Sellable cells may be purely visual (no zone/seat ids) — the designer places
        // shapes; the application layer validates ids only when present.
        LayoutCell seat = new LayoutCell(0, 0, LayoutCellType.SEAT, null, null, null);
        LayoutCell ga = new LayoutCell(0, 1, LayoutCellType.GENERAL_ADMISSION, "Floor", null, null);
        assertTrue(seat.isSellable());
        assertTrue(ga.isSellable());
    }

    @Test
    void GivenNegativeCoordinates_WhenConstruct_ThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new LayoutCell(-1, 0, LayoutCellType.BLOCKED, null, null, null));
    }

    @Test
    void GivenNonSellableCells_WhenConstruct_ThenNoZoneOrSeatRequired() {
        LayoutCell stage = LayoutCell.stage(0, 0, "Main Stage");
        LayoutCell blocked = LayoutCell.blocked(0, 1);
        assertTrue(!stage.isSellable());
        assertTrue(!blocked.isSellable());
    }

    @Test
    void GivenCellOutsideGrid_WhenConstructLayout_ThenThrows() {
        LayoutCell outOfBounds = LayoutCell.blocked(5, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new VenueLayout(2, 2, List.of(outOfBounds)));
    }

    @Test
    void GivenTwoCellsAtSamePosition_WhenConstructLayout_ThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new VenueLayout(2, 2, List.of(LayoutCell.blocked(0, 0), LayoutCell.stage(0, 0, "x"))));
    }

    @Test
    void GivenNonPositiveDimensions_WhenConstructLayout_ThenThrows() {
        assertThrows(IllegalArgumentException.class, () -> new VenueLayout(0, 3, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new VenueLayout(3, 0, List.of()));
    }

    @Test
    void GivenMixedLayout_WhenQuery_ThenReportsCellsAndReferences() {
        UUID zoneA = UUID.randomUUID();
        UUID seatA = UUID.randomUUID();
        UUID zoneGa = UUID.randomUUID();
        VenueLayout layout = new VenueLayout(2, 3, List.of(
                LayoutCell.seat(0, 0, zoneA, seatA),
                LayoutCell.ga(0, 1, zoneGa, "Floor"),
                LayoutCell.stage(1, 0, "Stage"),
                LayoutCell.blocked(1, 1)));

        assertEquals(2, layout.getRows());
        assertEquals(3, layout.getCols());
        assertTrue(layout.hasSellableCell());
        assertTrue(layout.cellAt(0, 0).isPresent());
        assertEquals(LayoutCellType.SEAT, layout.cellAt(0, 0).get().getType());
        assertTrue(layout.cellAt(0, 2).isEmpty());
        assertEquals(1, layout.cellsOfType(LayoutCellType.STAGE).size());
        assertTrue(layout.referencedZoneIds().contains(zoneA));
        assertTrue(layout.referencedZoneIds().contains(zoneGa));
        assertTrue(layout.referencedSeatIds().contains(seatA));
    }
}
