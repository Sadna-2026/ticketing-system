package com.ticketing.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.Seat;

/**
 * Public read queries against events. Guest-callable (no token required).
 * Returns the venue map + live availability for events that can be browsed
 * (PUBLISHED or SOLD_OUT). DRAFT, CANCELLED, and unknown events return empty.
 */
public class EventQueryService {

    private static final Logger log = LoggerFactory.getLogger(EventQueryService.class);

    private final IEventRepository eventRepository;

    public EventQueryService(IEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Optional<EventMapDTO> getEventMap(UUID eventId) {
        if (eventId == null) return Optional.empty();
        Optional<Event> maybe = eventRepository.findById(eventId);
        if (maybe.isEmpty()) {
            log.info("Event map request denied: id={}, reason=unknown", eventId);
            return Optional.empty();
        }
        Event event = maybe.get();
        if (!isBrowsable(event)) {
            log.info("Event map request denied: id={}, reason=status={}", eventId, event.getStatus());
            return Optional.empty();
        }

        List<EventMapDTO.ZoneInfo> zoneDtos = new ArrayList<>();
        for (InventoryZone zone : event.getZones()) {
            zoneDtos.add(toZoneInfo(zone));
        }

        return Optional.of(new EventMapDTO(
                event.getId(),
                event.getName(),
                event.getCompanyName(),
                event.getStatus(),
                event.getVenueMap() == null ? java.util.Map.of() : event.getVenueMap().getSectionToZone(),
                zoneDtos
        ));
    }

    private static boolean isBrowsable(Event e) {
        return e.getStatus() == EventStatus.PUBLISHED || e.getStatus() == EventStatus.SOLD_OUT;
    }

    private static EventMapDTO.ZoneInfo toZoneInfo(InventoryZone z) {
        if (z.isGA()) {
            return new EventMapDTO.ZoneInfo(
                    z.getId(), z.getName(), z.getType(), z.getPricePerTicket(),
                    z.getMaxCapacity(), z.getAvailableCount(), z.getLockedCount(), z.getSoldCount(),
                    List.of());
        }
        // assigned: include per-seat status
        List<EventMapDTO.SeatInfo> seats = new ArrayList<>();
        for (Seat s : z.getSeats()) {
            seats.add(new EventMapDTO.SeatInfo(s.getId(), s.getRow(), s.getSeatNumber(), s.getStatus()));
        }
        return new EventMapDTO.ZoneInfo(
                z.getId(), z.getName(), z.getType(), z.getPricePerTicket(),
                null, null, null, null,
                seats);
    }
}
