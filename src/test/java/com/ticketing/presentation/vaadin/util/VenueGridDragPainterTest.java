package com.ticketing.presentation.vaadin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("VenueGridDragPainter")
class VenueGridDragPainterTest {

    @Test
    @DisplayName("paintRange fills inclusive rectangle between anchor and target")
    void givenAnchorAndTarget_whenPaintRange_thenAllCellsInBlockPainted() {
        List<String> painted = new ArrayList<>();
        VenueGridDragPainter.paintRange(
                (row, col) -> painted.add(row + "," + col),
                0, 0, 1, 1);

        assertEquals(List.of("0,0", "0,1", "1,0", "1,1"), painted);
    }

    @Test
    @DisplayName("paintRange works when target is above-left of anchor")
    void givenReversedCorners_whenPaintRange_thenSameBlock() {
        List<String> painted = new ArrayList<>();
        VenueGridDragPainter.paintRange(
                (row, col) -> painted.add(row + "," + col),
                2, 3, 0, 1);

        assertEquals(List.of("0,1", "0,2", "0,3", "1,1", "1,2", "1,3", "2,1", "2,2", "2,3"), painted);
    }
}
