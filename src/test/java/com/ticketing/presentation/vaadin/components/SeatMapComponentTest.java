package com.ticketing.presentation.vaadin.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ticketing.application.dto.EventMapDTO;

import elemental.json.JsonArray;
import elemental.json.JsonObject;

class SeatMapComponentTest {

    @Test
    void stagedSelectionIsTrackedOnServerAndIncludedInSyncPayload() {
        UUID freeSeat = UUID.randomUUID();
        UUID takenSeat = UUID.randomUUID();
        List<EventMapDTO.SeatInfo> seats = List.of(
                new EventMapDTO.SeatInfo(freeSeat, "A", "1", true),
                new EventMapDTO.SeatInfo(takenSeat, "A", "2", false));

        SeatMapComponent map = new SeatMapComponent(seats);
        map.onStagedSelectionChanged(new String[] { freeSeat.toString() });

        assertEquals(1, map.stagedSeatIds().size());
        assertTrue(map.stagedSeatIds().contains(freeSeat));

        map.syncAvailability(seats, lost -> assertTrue(lost.isEmpty()));

        JsonArray payload = (JsonArray) map.getElement().getPropertyRaw("seats");
        assertTrue(seatEntry(payload, freeSeat).getBoolean("selected"));
        assertFalse(seatEntry(payload, takenSeat).getBoolean("selected"));
    }

    @Test
    void syncAvailabilityDropsStagedSeatsThatBecameTaken() {
        UUID seatId = UUID.randomUUID();
        List<EventMapDTO.SeatInfo> initial = List.of(new EventMapDTO.SeatInfo(seatId, "B", "3", true));
        SeatMapComponent map = new SeatMapComponent(initial);
        map.onStagedSelectionChanged(new String[] { seatId.toString() });

        List<EventMapDTO.SeatInfo> updated = List.of(new EventMapDTO.SeatInfo(seatId, "B", "3", false));
        map.syncAvailability(updated, lost -> assertEquals(List.of("B-3"), lost));

        assertTrue(map.stagedSeatIds().isEmpty());
    }

    private JsonObject seatEntry(JsonArray seats, UUID seatId) {
        for (int i = 0; i < seats.length(); i++) {
            JsonObject seat = seats.getObject(i);
            if (seatId.toString().equals(seat.getString("id"))) {
                return seat;
            }
        }
        throw new AssertionError("Seat not found: " + seatId);
    }
}
