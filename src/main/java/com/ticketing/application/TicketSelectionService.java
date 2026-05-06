package com.ticketing.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.Seat;
import com.ticketing.infrastructure.Interface.IEventRepository;

/**
 * Pre-reservation validation per UC-II.8. Confirms the requested seats / GA
 * quantities exist on the event, the event is browsable, and nothing in the
 * selection is currently locked or sold. Does NOT mutate state — actual
 * locking is done by UC-II.9-10.
 */
public class TicketSelectionService {

    private static final Logger log = LoggerFactory.getLogger(TicketSelectionService.class);

    private final IEventRepository eventRepository;

    public TicketSelectionService(IEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public void validateSelection(SelectionRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        if (request.isEmpty()) {
            throw new IllegalArgumentException("selection must include at least one seat or quantity");
        }

        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + request.eventId()));

        if (!isSelectable(event)) {
            throw new IllegalStateException(
                    "Event is not selectable in status: " + event.getStatus());
        }

        for (SelectionRequest.SeatPick pick : request.seats()) {
            InventoryZone zone = findZone(event, pick.zoneId());
            if (!zone.isAssigned()) {
                throw new IllegalArgumentException(
                        "Zone " + zone.getName() + " is GA — use a quantity, not a seat id");
            }
            Seat seat;
            try {
                seat = zone.findSeat(pick.seatId());
            } catch (IllegalArgumentException notFound) {
                throw new IllegalArgumentException(
                        "Seat " + pick.seatId() + " not found in zone " + zone.getName());
            }
            if (!seat.isAvailable()) {
                throw new IllegalStateException(
                        "Seat " + seat.getRow() + "-" + seat.getSeatNumber()
                        + " is not available (status=" + seat.getStatus() + ")");
            }
        }

        // Aggregate GA picks per zone first — multiple picks targeting the same zone
        // must be summed before the supply check, otherwise two picks of 4 each could
        // both pass when only 5 are available.
        java.util.Map<java.util.UUID, Integer> totalsByZone = new java.util.HashMap<>();
        for (SelectionRequest.GAPick pick : request.gaQuantities()) {
            totalsByZone.merge(pick.zoneId(), pick.quantity(), Integer::sum);
        }
        for (var entry : totalsByZone.entrySet()) {
            InventoryZone zone = findZone(event, entry.getKey());
            int requested = entry.getValue();
            if (!zone.isGA()) {
                throw new IllegalArgumentException(
                        "Zone " + zone.getName() + " is assigned-seating — pick specific seats, not a quantity");
            }
            if (zone.getAvailableCount() < requested) {
                throw new IllegalStateException(
                        "Not enough tickets in zone " + zone.getName()
                        + " (requested " + requested + ", available " + zone.getAvailableCount() + ")");
            }
        }

        log.info("Selection validated: eventId={}, seats={}, gaPicks={}",
                event.getId(), request.seats().size(), request.gaQuantities().size());
    }

    private static boolean isSelectable(Event e) {
        // SOLD_OUT events are visible (per II.7) but you can't pick tickets from them
        return e.getStatus() == EventStatus.PUBLISHED;
    }

    private static InventoryZone findZone(Event event, java.util.UUID zoneId) {
        try {
            return event.findZone(zoneId);
        } catch (IllegalArgumentException notFound) {
            throw new IllegalArgumentException(
                    "Zone " + zoneId + " is not part of event " + event.getId());
        }
    }
}
