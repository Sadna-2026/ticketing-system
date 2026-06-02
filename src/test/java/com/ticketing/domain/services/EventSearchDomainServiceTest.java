package com.ticketing.domain.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;

@DisplayName("EventSearchDomainService.findCompanyEvents")
class EventSearchDomainServiceTest {

    private InMemoryEventRepository eventRepository;
    private EventSearchDomainService service;

    @BeforeEach
    void setUp() {
        eventRepository = new InMemoryEventRepository();
        service = new EventSearchDomainService(eventRepository, new InMemoryCompanyRepository());
    }

    @Test
    void GivenCompanyWithEvents_WhenFindingCompanyEvents_ThenOnlyThatCompanysEventsReturnedSortedByName() {
        eventRepository.save(draftEvent("Acme", "Spring Show"));
        eventRepository.save(draftEvent("Acme", "Autumn Show"));
        eventRepository.save(draftEvent("Other", "Beta Show"));

        List<EventSummaryDTO> results = service.findCompanyEvents("Acme");

        assertEquals(List.of("Autumn Show", "Spring Show"), results.stream().map(EventSummaryDTO::name).toList());
    }

    @Test
    void GivenDraftAndCancelledEvents_WhenFindingCompanyEvents_ThenNonBrowsableStatusesAreIncluded() {
        eventRepository.save(draftEvent("Acme", "Draft Show"));
        Event cancelled = draftEvent("Acme", "Cancelled Show");
        cancelled.cancel();
        eventRepository.save(cancelled);

        List<EventSummaryDTO> results = service.findCompanyEvents("Acme");

        // Unlike the public searchEvents, management listing must surface drafts/cancelled so
        // they can be published or managed.
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(e -> e.status() == EventStatus.DRAFT));
        assertTrue(results.stream().anyMatch(e -> e.status() == EventStatus.CANCELLED));
    }

    @Test
    void GivenBlankCompanyName_WhenFindingCompanyEvents_ThenEmptyListReturned() {
        eventRepository.save(draftEvent("Acme", "Spring Show"));

        assertTrue(service.findCompanyEvents("  ").isEmpty());
        assertTrue(service.findCompanyEvents(null).isEmpty());
    }

    private static Event draftEvent(String companyName, String name) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new Event(
                UUID.randomUUID(),
                companyName,
                name,
                "desc",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS)),
                new LockTimerDuration(Duration.ofMinutes(15))
        );
    }
}
