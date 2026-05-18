package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.event.VenueMap;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;

@DisplayName("Event read services")
class EventReadServicesTest {

    @Nested
    @DisplayName("Query")
    class Query {

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
            assertEquals(5, gaInfo.soldCount());
            // lockedCount is intentionally not exposed in the public DTO

            EventMapDTO.ZoneInfo vipInfo = findZone(dto, vipZoneId);
            assertEquals(3, vipInfo.seats().size());
            long unavailable = vipInfo.seats().stream().filter(s -> !s.available()).count();
            assertEquals(1, unavailable, "the locked seat shows as unavailable");
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
            attachVenueMap(e, Map.of("S", gaZoneId));
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
        public void GivenPublishedEventWithoutVenueMap_WhenGetEventMap_ThenReturnsEmpty() {
            UUID eventId = UUID.randomUUID();
            Event e = newEvent(eventId, "Misconfigured");
            addGAZone(e, "Floor", new BigDecimal("10.00"), 10);
            e.publish(); // publish() does NOT require a venueMap
            eventRepo.save(e);

            assertTrue(service.getEventMap(eventId).isEmpty(),
                    "events without a venue map cannot render and shouldn't be browsable");
        }

        @Test
        public void GivenDtoReturned_WhenSourceMutated_ThenDtoUnchanged() {
            UUID eventId = UUID.randomUUID();
            Event e = newEvent(eventId, "Live");
            UUID gaZoneId = addGAZone(e, "Floor", new BigDecimal("10.00"), 10);
            attachVenueMap(e, Map.of("S", gaZoneId));
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
            UUID gaZoneId = addGAZone(e, "Floor", new BigDecimal("10.00"), 10);
            attachVenueMap(e, Map.of("S", gaZoneId));
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

    @Nested
    @DisplayName("Search")
    class Search {

        private static final String COMPANY_A = "Acme Productions";
        private static final String COMPANY_B = "Beta Music";

        private InMemoryEventRepository eventRepo;
        private InMemoryCompanyRepository companyRepo;
        private EventSearchService service;

        @BeforeEach
        public void setUp() {
            eventRepo = new InMemoryEventRepository();
            companyRepo = new InMemoryCompanyRepository();
            service = new EventSearchService(eventRepo, companyRepo);

            companyRepo.save(new Company(COMPANY_A, "x", UUID.randomUUID()));
            companyRepo.save(new Company(COMPANY_B, "x", UUID.randomUUID()));
        }

        @Test
        public void GivenMatchingNameAndPublishedEvent_WhenSearch_ThenReturnsHit() {
            publish(COMPANY_A, "Spring Concert", "Beethoven", EventCategory.CONCERT,
                    "Tel Aviv", new BigDecimal("50.00"), 30);

            List<EventSummaryDTO> hits = service.searchEvents(
                    new SearchEventsRequest("spring", null, null, null, null, null, null, null));

            assertEquals(1, hits.size());
            assertEquals("Spring Concert", hits.get(0).name());
        }

        @Test
        public void GivenNoFilters_WhenSearch_ThenReturnsAllVisibleEvents() {
            publish(COMPANY_A, "Concert 1", "X", EventCategory.CONCERT, "TLV", new BigDecimal("10"), 30);
            publish(COMPANY_B, "Concert 2", "Y", EventCategory.FESTIVAL, "JLM", new BigDecimal("20"), 60);

            List<EventSummaryDTO> hits = service.searchEvents(SearchEventsRequest.empty());

            assertEquals(2, hits.size());
        }

        @Test
        public void GivenNoMatch_WhenSearch_ThenReturnsEmptyListNotError() {
            publish(COMPANY_A, "Spring Concert", "Beethoven", EventCategory.CONCERT,
                    "TLV", new BigDecimal("50"), 30);

            List<EventSummaryDTO> hits = service.searchEvents(
                    new SearchEventsRequest("nonexistent", null, null, null, null, null, null, null));

            assertTrue(hits.isEmpty(), "no error, just empty list");
        }

        @Test
        public void GivenMultipleFilters_WhenSearch_ThenAndSemanticsApply() {
            publish(COMPANY_A, "Jazz Night",  "Miles", EventCategory.CONCERT,  "TLV", new BigDecimal("80"), 30);
            publish(COMPANY_A, "Jazz Brunch", "Miles", EventCategory.FESTIVAL, "TLV", new BigDecimal("80"), 30);
            publish(COMPANY_A, "Jazz Night",  "Miles", EventCategory.CONCERT,  "JLM", new BigDecimal("80"), 30);

            // text=jazz AND category=CONCERT AND region=TLV → only the first
            SearchEventsRequest req = new SearchEventsRequest(
                    "jazz", "TLV", EventCategory.CONCERT, null, null, null, null, null);

            List<EventSummaryDTO> hits = service.searchEvents(req);
            assertEquals(1, hits.size());
            assertEquals("Jazz Night", hits.get(0).name());
            assertEquals(EventCategory.CONCERT, hits.get(0).category());
        }

        @Test
        public void GivenCancelledEvent_WhenSearch_ThenExcluded() {
            UUID id = UUID.randomUUID();
            Event e = newEvent(id, COMPANY_A, "Cancelled Concert", "X", EventCategory.CONCERT,
                    "TLV", new BigDecimal("50"), 30);
            e.publish();
            e.cancel();
            eventRepo.save(e);

            List<EventSummaryDTO> hits = service.searchEvents(SearchEventsRequest.empty());
            assertTrue(hits.isEmpty());
        }

        @Test
        public void GivenDraftEvent_WhenSearch_ThenExcluded() {
            UUID id = UUID.randomUUID();
            Event e = newEvent(id, COMPANY_A, "Draft Event", "X", EventCategory.CONCERT,
                    "TLV", new BigDecimal("50"), 30);
            // intentionally not publishing
            eventRepo.save(e);

            assertTrue(service.searchEvents(SearchEventsRequest.empty()).isEmpty());
        }

        @Test
        public void GivenEventOfSuspendedCompany_WhenSearch_ThenExcluded() {
            publish(COMPANY_A, "Hidden", "X", EventCategory.CONCERT, "TLV", new BigDecimal("50"), 30);
            Company a = companyRepo.findByName(COMPANY_A).orElseThrow();
            a.suspend();
            companyRepo.save(a);

            assertTrue(service.searchEvents(SearchEventsRequest.empty()).isEmpty());
        }

        @Test
        public void GivenEventOfClosedCompany_WhenSearch_ThenExcluded() {
            publish(COMPANY_A, "Hidden", "X", EventCategory.CONCERT, "TLV", new BigDecimal("50"), 30);
            Company a = companyRepo.findByName(COMPANY_A).orElseThrow();
            a.close();
            companyRepo.save(a);

            assertTrue(service.searchEvents(SearchEventsRequest.empty()).isEmpty());
        }

        @Test
        public void GivenCompanyNameFilter_WhenSearch_ThenOnlyThatCompanysEvents() {
            publish(COMPANY_A, "Show A", "X", EventCategory.CONCERT, "TLV", new BigDecimal("50"), 30);
            publish(COMPANY_B, "Show B", "X", EventCategory.CONCERT, "TLV", new BigDecimal("50"), 30);

            List<EventSummaryDTO> hits = service.searchEvents(
                    new SearchEventsRequest(null, null, null, COMPANY_A, null, null, null, null));

            assertEquals(1, hits.size());
            assertEquals("Show A", hits.get(0).name());
        }

        @Test
        public void GivenPriceRange_WhenSearch_ThenOnlyEventsWithMatchingPriceZone() {
            publish(COMPANY_A, "Cheap",  "X", EventCategory.CONCERT, "TLV", new BigDecimal("20"), 30);
            publish(COMPANY_A, "Pricey", "X", EventCategory.CONCERT, "TLV", new BigDecimal("200"), 30);

            // price between 50 and 100 → neither matches
            List<EventSummaryDTO> noHits = service.searchEvents(
                    new SearchEventsRequest(null, null, null, null,
                            new BigDecimal("50"), new BigDecimal("100"), null, null));
            assertTrue(noHits.isEmpty());

            // price up to 50 → only Cheap
            List<EventSummaryDTO> cheap = service.searchEvents(
                    new SearchEventsRequest(null, null, null, null,
                            null, new BigDecimal("50"), null, null));
            assertEquals(1, cheap.size());
            assertEquals("Cheap", cheap.get(0).name());
        }

        @Test
        public void GivenDateRange_WhenSearch_ThenOnlyEventsInRange() {
            publish(COMPANY_A, "Soon",  "X", EventCategory.CONCERT, "TLV", new BigDecimal("50"), 10); // ~10d
            publish(COMPANY_A, "Later", "X", EventCategory.CONCERT, "TLV", new BigDecimal("50"), 90); // ~90d

            Instant from = Instant.now().plus(30, ChronoUnit.DAYS);
            Instant to = Instant.now().plus(120, ChronoUnit.DAYS);

            List<EventSummaryDTO> hits = service.searchEvents(
                    new SearchEventsRequest(null, null, null, null, null, null, from, to));
            assertEquals(1, hits.size());
            assertEquals("Later", hits.get(0).name());
        }

        @Test
        public void GivenNullRequest_WhenSearch_ThenTreatedAsEmptyFilters() {
            publish(COMPANY_A, "Show", "X", EventCategory.CONCERT, "TLV", new BigDecimal("50"), 30);

            List<EventSummaryDTO> hits = service.searchEvents(null);
            assertEquals(1, hits.size());
        }

        @Test
        public void GivenRegionFilterWithDifferentCase_WhenSearch_ThenStillMatches() {
            publish(COMPANY_A, "Show", "X", EventCategory.CONCERT, "Tel Aviv", new BigDecimal("50"), 30);

            List<EventSummaryDTO> hits = service.searchEvents(
                    new SearchEventsRequest(null, "tel aviv", null, null, null, null, null, null));
            assertEquals(1, hits.size());
        }

        @Test
        public void GivenInvertedPriceRange_WhenBuildRequest_ThenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SearchEventsRequest(null, null, null, null,
                            new BigDecimal("100"), new BigDecimal("50"), null, null));
        }

        @Test
        public void GivenInvertedDateRange_WhenBuildRequest_ThenThrowIllegalArgumentException() {
            Instant later = Instant.now().plus(60, ChronoUnit.DAYS);
            Instant earlier = Instant.now().plus(10, ChronoUnit.DAYS);
            assertThrows(IllegalArgumentException.class,
                    () -> new SearchEventsRequest(null, null, null, null, null, null, later, earlier));
        }

        // helpers

        private void publish(String companyName, String name, String artist, EventCategory category,
                             String region, BigDecimal price, int daysFromNow) {
            UUID id = UUID.randomUUID();
            Event e = newEvent(id, companyName, name, artist, category, region, price, daysFromNow);
            e.publish();
            eventRepo.save(e);
        }

        private static Event newEvent(UUID id, String companyName, String name, String artist,
                                      EventCategory category, String region, BigDecimal price,
                                      int daysFromNow) {
            Instant start = Instant.now().plus(daysFromNow, ChronoUnit.DAYS);
            EventSchedule s = new EventSchedule(
                    start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS));
            Event e = new Event(id, companyName, name, "desc", category, s,
                    new LockTimerDuration(Duration.ofMinutes(15)));
            e.setArtist(artist);
            e.setRegion(region);
            e.addZone(InventoryZone.createGA(UUID.randomUUID(), "Floor", price, 50));
            return e;
        }
    }
}
