package com.ticketing.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.event.SeatStatus;
import com.ticketing.domain.event.VenueMap;
import com.ticketing.infrastructure.InMemoryEventRepository;

public class EventQueryServiceTest {

    private static final String COMPANY = "Acme Productions";

    private InMemoryEventRepository eventRepo;
    private EventQueryService service;

    @BeforeEach
    public void setUp() {
        eventRepo = new InMemoryEventRepository();
        service = new EventQueryService(eventRepo);
    }

    @Test
    public void GivenPublishedEvent_WhenGetEventMap_ThenReturnsMapAndInventory() {
        UUID eventId = UUID.randomUUID();
        Event e = newEvent(eventId, "Spring Concert");
        UUID gaZoneId = addGAZone(e, "Floor", new BigDecimal("50.00"), 100);
        UUID vipZoneId = addAssignedZone(e, "VIP", new BigDecimal("150.00"), 4);
        attachVenueMap(e, Map.of("Section A", gaZoneId, "VIP Boxes", vipZoneId));
        e.publish();
        eventRepo.save(e);

        EventMapDTO dto = service.getEventMap(eventId).orElseThrow();

        assertEquals(eventId, dto.eventId());
        assertEquals("Spring Concert", dto.eventName());
        assertEquals(COMPANY, dto.companyName());
        assertEquals(EventStatus.PUBLISHED, dto.status());
        assertEquals(2, dto.venueMap().size());
        assertEquals(2, dto.zones().size());
    }

    @Test
    public void GivenLiveZoneState_WhenGetEventMap_ThenAvailabilityCountsReflectIt() {
        UUID eventId = UUID.randomUUID();
        Event e = newEvent(eventId, "Live");
        UUID gaZoneId = addGAZone(e, "Floor", new BigDecimal("40.00"), 100);
        UUID vipZoneId = addAssignedZone(e, "VIP", new BigDecimal("200.00"), 3);
        attachVenueMap(e, Map.of("S", gaZoneId, "V", vipZoneId));
        e.publish();
        // mutate live state
        InventoryZone ga = e.findZone(gaZoneId);
        ga.lockGA(20);
        ga.sellGA(5); // 5 sold, 15 still locked, 80 free
        InventoryZone vip = e.findZone(vipZoneId);
        UUID firstSeat = vip.getSeats().get(0).getId();
        vip.lockSeat(firstSeat); // 1 locked, 2 free
        eventRepo.save(e);

        EventMapDTO dto = service.getEventMap(eventId).orElseThrow();

        EventMapDTO.ZoneInfo gaInfo = findZone(dto, gaZoneId);
        assertEquals(80, gaInfo.availableCount());
        assertEquals(15, gaInfo.lockedCount());
        assertEquals(5, gaInfo.soldCount());

        EventMapDTO.ZoneInfo vipInfo = findZone(dto, vipZoneId);
        assertEquals(3, vipInfo.seats().size());
        long lockedSeats = vipInfo.seats().stream()
                .filter(s -> s.status() == SeatStatus.LOCKED).count();
        assertEquals(1, lockedSeats);
    }

    @Test
    public void GivenUnknownEventId_WhenGetEventMap_ThenReturnsEmpty() {
        assertTrue(service.getEventMap(UUID.randomUUID()).isEmpty());
    }

    @Test
    public void GivenNullEventId_WhenGetEventMap_ThenReturnsEmpty() {
        assertTrue(service.getEventMap(null).isEmpty());
    }

    @Test
    public void GivenCancelledEvent_WhenGetEventMap_ThenReturnsEmpty() {
        UUID eventId = UUID.randomUUID();
        Event e = newEvent(eventId, "Cancelled Concert");
        addGAZone(e, "Floor", new BigDecimal("40.00"), 50);
        e.publish();
        e.cancel();
        eventRepo.save(e);

        assertTrue(service.getEventMap(eventId).isEmpty(),
                "cancelled events are not browsable for reservation");
    }

    @Test
    public void GivenDraftEvent_WhenGetEventMap_ThenReturnsEmpty() {
        UUID eventId = UUID.randomUUID();
        Event e = newEvent(eventId, "Draft");
        addGAZone(e, "Floor", new BigDecimal("10.00"), 10);
        eventRepo.save(e); // not published

        assertTrue(service.getEventMap(eventId).isEmpty());
    }

    @Test
    public void GivenSoldOutEvent_WhenGetEventMap_ThenReturnsDtoWithSoldOutStatus() {
        UUID eventId = UUID.randomUUID();
        Event e = newEvent(eventId, "Hot Show");
        UUID gaZoneId = addGAZone(e, "Floor", new BigDecimal("30.00"), 5);
        e.publish();
        InventoryZone ga = e.findZone(gaZoneId);
        ga.lockGA(5);
        ga.sellGA(5);
        e.markSoldOut();
        eventRepo.save(e);

        EventMapDTO dto = service.getEventMap(eventId).orElseThrow();
        assertEquals(EventStatus.SOLD_OUT, dto.status());
    }

    @Test
    public void GivenDtoReturned_WhenSourceMutated_ThenDtoUnchanged() {
        UUID eventId = UUID.randomUUID();
        Event e = newEvent(eventId, "Live");
        addGAZone(e, "Floor", new BigDecimal("10.00"), 10);
        e.publish();
        eventRepo.save(e);

        EventMapDTO dto = service.getEventMap(eventId).orElseThrow();
        int before = dto.zones().size();

        // mutate source after DTO returned
        e.findZone(dto.zones().get(0).id()).lockGA(3);

        // DTO is a snapshot — its zones list size shouldn't change
        assertEquals(before, dto.zones().size());
    }

    @Test
    public void GivenDtoReturned_WhenCallerMutatesZonesList_ThenThrows() {
        UUID eventId = UUID.randomUUID();
        Event e = newEvent(eventId, "Live");
        addGAZone(e, "Floor", new BigDecimal("10.00"), 10);
        e.publish();
        eventRepo.save(e);

        EventMapDTO dto = service.getEventMap(eventId).orElseThrow();

        assertThrows(UnsupportedOperationException.class, () -> dto.zones().clear());
    }

    // helpers

    private static Event newEvent(UUID id, String name) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new Event(id, COMPANY, name, "desc", EventCategory.CONCERT,
                new EventSchedule(start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS)),
                new LockTimerDuration(Duration.ofMinutes(15)));
    }

    private static UUID addGAZone(Event e, String name, BigDecimal price, int capacity) {
        UUID id = UUID.randomUUID();
        e.addZone(InventoryZone.createGA(id, name, price, capacity));
        return id;
    }

    private static UUID addAssignedZone(Event e, String name, BigDecimal price, int seatCount) {
        UUID id = UUID.randomUUID();
        InventoryZone z = InventoryZone.createAssigned(id, name, price);
        for (int i = 1; i <= seatCount; i++) {
            z.addSeat(new Seat(UUID.randomUUID(), "A", String.valueOf(i)));
        }
        e.addZone(z);
        return id;
    }

    /** Build a venue map that references all of the event's zones (bijection). */
    private static void attachVenueMap(Event e, Map<String, UUID> sectionToZoneId) {
        Map<String, UUID> copy = new HashMap<>(sectionToZoneId);
        e.setVenueMap(new VenueMap(copy));
    }

    private static EventMapDTO.ZoneInfo findZone(EventMapDTO dto, UUID zoneId) {
        EventMapDTO.ZoneInfo found = dto.zones().stream()
                .filter(z -> z.id().equals(zoneId)).findFirst().orElse(null);
        assertNotNull(found, "zone not in DTO: " + zoneId);
        return found;
    }
}
