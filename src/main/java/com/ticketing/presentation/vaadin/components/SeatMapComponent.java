package com.ticketing.presentation.vaadin.components;

import java.util.List;
import java.util.UUID;

import com.ticketing.application.dto.EventMapDTO;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasEnabled;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.function.SerializableConsumer;

import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;

/**
 * A scalable assigned-seating map. Instead of rendering one server-side
 * {@link com.vaadin.flow.component.button.Button} per seat (the approach #254
 * shipped, which does not scale past a few seats — see issue #255), this is a
 * single client-side {@code <seat-map>} Lit element fed a compact, already
 * row/seat-ordered seat payload. Selection is hit-tested in the browser; only
 * the chosen seat id is sent back via {@link #selectSeat(String)}, which routes
 * through the existing reservation/lock flow. After a successful add the view
 * flips just that seat to taken with {@link #markSeatTaken(UUID)} — no full
 * re-render.
 */
@Tag("seat-map")
@JsModule("./seat-map.js")
public class SeatMapComponent extends Component implements HasEnabled {

    private transient SerializableConsumer<UUID> selectionListener;

    /**
     * @param orderedSeats seats already ordered by row then seat number; the
     *                     client renders them in the given order.
     */
    public SeatMapComponent(List<EventMapDTO.SeatInfo> orderedSeats) {
        JsonArray payload = Json.createArray();
        int i = 0;
        for (EventMapDTO.SeatInfo seat : orderedSeats) {
            JsonObject entry = Json.createObject();
            entry.put("id", seat.id().toString());
            entry.put("row", seat.row());
            entry.put("num", seat.seatNumber());
            entry.put("taken", !seat.available());
            payload.set(i++, entry);
        }
        getElement().setPropertyJson("seats", payload);
    }

    /** Registers the callback invoked when the buyer clicks a free seat. */
    public void setSelectionListener(SerializableConsumer<UUID> listener) {
        this.selectionListener = listener;
    }

    /** Invoked from the client when a free seat is clicked. */
    @ClientCallable
    public void selectSeat(String seatId) {
        if (seatId == null || seatId.isBlank() || selectionListener == null) {
            return;
        }
        selectionListener.accept(UUID.fromString(seatId));
    }

    /** Flips a single seat to taken on the client after a successful add. */
    public void markSeatTaken(UUID seatId) {
        if (seatId != null) {
            getElement().callJsFunction("markTaken", seatId.toString());
        }
    }
}
