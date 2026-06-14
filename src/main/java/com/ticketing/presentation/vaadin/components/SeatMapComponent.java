package com.ticketing.presentation.vaadin.components;

import java.util.ArrayList;
import java.util.Collection;
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
 * Assigned-seating map with staged selection: clicks toggle amber "your selection"
 * seats locally; inventory is locked only when {@link #requestAddSelection()} runs.
 */
@Tag("seat-map")
@JsModule("./seat-map.js")
public class SeatMapComponent extends Component implements HasEnabled {

    private transient SerializableConsumer<Integer> selectionCountListener;
    private transient SerializableConsumer<List<UUID>> commitListener;
    private transient SerializableConsumer<List<String>> syncCompleteListener;

    public SeatMapComponent(List<EventMapDTO.SeatInfo> orderedSeats) {
        JsonArray payload = Json.createArray();
        int i = 0;
        for (EventMapDTO.SeatInfo seat : orderedSeats) {
            JsonObject entry = Json.createObject();
            entry.put("id", seat.id().toString());
            entry.put("row", seat.row());
            entry.put("num", seat.seatNumber());
            entry.put("taken", !seat.available());
            entry.put("selected", false);
            payload.set(i++, entry);
        }
        getElement().setPropertyJson("seats", payload);
    }

    public void setSelectionCountListener(SerializableConsumer<Integer> listener) {
        this.selectionCountListener = listener;
    }

    public void setCommitListener(SerializableConsumer<List<UUID>> listener) {
        this.commitListener = listener;
    }

    /** Ask the client to send staged seat ids to {@link #onCommitSelection(String[])}. */
    public void requestAddSelection() {
        getElement().callJsFunction("requestAddSelection");
    }

    @ClientCallable
    public void notifySelectionCount(int count) {
        if (selectionCountListener != null) {
            selectionCountListener.accept(count);
        }
    }

    @ClientCallable
    public void onCommitSelection(String[] seatIds) {
        if (commitListener == null || seatIds == null || seatIds.length == 0) {
            return;
        }
        List<UUID> ids = new ArrayList<>(seatIds.length);
        for (String seatId : seatIds) {
            if (seatId != null && !seatId.isBlank()) {
                ids.add(UUID.fromString(seatId));
            }
        }
        if (!ids.isEmpty()) {
            commitListener.accept(ids);
        }
    }

    public void markSeatsTaken(Collection<UUID> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            return;
        }
        for (UUID seatId : seatIds) {
            if (seatId != null) {
                getElement().callJsFunction("markTakenMany", seatId.toString());
            }
        }
    }

    /**
     * Refresh taken/available state from the server and clear staged picks on seats
     * that are no longer free. {@code onComplete} receives row-seat labels removed from selection.
     */
    public void syncAvailability(List<EventMapDTO.SeatInfo> freshSeats, SerializableConsumer<List<String>> onComplete) {
        this.syncCompleteListener = onComplete;
        JsonArray payload = Json.createArray();
        int i = 0;
        for (EventMapDTO.SeatInfo seat : freshSeats) {
            JsonObject entry = Json.createObject();
            entry.put("id", seat.id().toString());
            entry.put("row", seat.row());
            entry.put("num", seat.seatNumber());
            entry.put("taken", !seat.available());
            payload.set(i++, entry);
        }
        getElement().callJsFunction("syncSeats", payload);
    }

    @ClientCallable
    public void onSyncComplete(String[] lostLabels) {
        List<String> lost = new ArrayList<>();
        if (lostLabels != null) {
            for (String label : lostLabels) {
                if (label != null && !label.isBlank()) {
                    lost.add(label);
                }
            }
        }
        if (syncCompleteListener != null) {
            syncCompleteListener.accept(lost);
            syncCompleteListener = null;
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        getElement().setProperty("disabled", !enabled);
    }

    @Override
    public boolean isEnabled() {
        return !getElement().getProperty("disabled", false);
    }
}
