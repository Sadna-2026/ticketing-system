package com.ticketing.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;

public class EventSearchServiceTest {

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
