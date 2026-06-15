package com.ticketing.presentation.vaadin.components;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    private final Set<UUID> stagedSeatIds = new LinkedHashSet<>();

    private transient SerializableConsumer<Integer> selectionCountListener;
    private transient SerializableConsumer<List<UUID>> commitListener;
    private transient SerializableConsumer<List<String>> syncCompleteListener;

    public SeatMapComponent(List<EventMapDTO.SeatInfo> orderedSeats) {
        applySeatsProperty(orderedSeats);
        getElement().addAttachListener(event -> restoreStagedSelectionOnClient());
    }

    /** Exposed for tests: staged picks survive route navigation while the map is off-screen. */
    Set<UUID> stagedSeatIds() {
        return Set.copyOf(stagedSeatIds);
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

    /** Keeps staged seat ids on the server so tab switches can restore client selection. */
    @ClientCallable
    public void onStagedSelectionChanged(String[] seatIds) {
        stagedSeatIds.clear();
        if (seatIds != null) {
            for (String seatId : seatIds) {
                if (seatId != null && !seatId.isBlank()) {
                    stagedSeatIds.add(UUID.fromString(seatId));
                }
            }
        }
        notifySelectionCount(stagedSeatIds.size());
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
        seatIds.stream().filter(java.util.Objects::nonNull).forEach(stagedSeatIds::remove);
        JsonArray payload = Json.createArray();
        int i = 0;
        for (UUID seatId : seatIds) {
            if (seatId != null) {
                payload.set(i++, seatId.toString());
            }
        }
        if (payload.length() > 0) {
            getElement().callJsFunction("markTakenMany", payload);
        }
        notifySelectionCount(stagedSeatIds.size());
    }

    /**
     * Refresh taken/available state from the server and clear staged picks on seats
     * that are no longer free. {@code onComplete} receives row-seat labels removed from selection.
     */
    public void syncAvailability(List<EventMapDTO.SeatInfo> freshSeats, SerializableConsumer<List<String>> onComplete) {
        this.syncCompleteListener = onComplete;
        stagedSeatIds.removeIf(id -> freshSeats.stream()
                .noneMatch(seat -> seat.id().equals(id) && seat.available()));
        JsonArray payload = buildSeatPayload(freshSeats);
        getElement().setPropertyJson("seats", payload);
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

    private void applySeatsProperty(List<EventMapDTO.SeatInfo> orderedSeats) {
        getElement().setPropertyJson("seats", buildSeatPayload(orderedSeats));
    }

    private JsonArray buildSeatPayload(List<EventMapDTO.SeatInfo> orderedSeats) {
        JsonArray payload = Json.createArray();
        int i = 0;
        for (EventMapDTO.SeatInfo seat : orderedSeats) {
            JsonObject entry = Json.createObject();
            entry.put("id", seat.id().toString());
            entry.put("row", seat.row());
            entry.put("num", seat.seatNumber());
            boolean taken = !seat.available();
            entry.put("taken", taken);
            entry.put("selected", !taken && stagedSeatIds.contains(seat.id()));
            payload.set(i++, entry);
        }
        return payload;
    }

    private void restoreStagedSelectionOnClient() {
        if (stagedSeatIds.isEmpty()) {
            notifySelectionCount(0);
            return;
        }
        JsonArray payload = Json.createArray();
        int i = 0;
        for (UUID seatId : stagedSeatIds) {
            payload.set(i++, seatId.toString());
        }
        getElement().callJsFunction("applyStagedSelection", payload);
        notifySelectionCount(stagedSeatIds.size());
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
