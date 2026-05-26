package com.ticketing.presentation.vaadin.presenters;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketing.application.SearchEventsRequest;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.application.services.EventQueryService;
import com.ticketing.application.services.EventSearchService;
import com.ticketing.domain.event.EventCategory;

@Component
public class EventsPresenter {

    private static final Logger logger = LoggerFactory.getLogger(EventsPresenter.class);

    private static final String EMPTY_SEARCH_MESSAGE = "No events found for the current filters.";
    private static final String SEARCH_FAILURE_MESSAGE = "Could not search events. Please try again.";
    private static final String MAP_NOT_FOUND_MESSAGE = "Event map not found.";
    private static final String MAP_FAILURE_MESSAGE = "Could not load event map. Please try again.";

    private final EventSearchService eventSearchService;
    private final EventQueryService eventQueryService;

    public EventsPresenter(EventSearchService eventSearchService, EventQueryService eventQueryService) {
        this.eventSearchService = eventSearchService;
        this.eventQueryService = eventQueryService;
    }

    public SearchResult searchEvents(
            String text,
            String region,
            EventCategory category,
            String companyName,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        try {
            SearchEventsRequest request = new SearchEventsRequest(
                    blankToNull(text),
                    blankToNull(region),
                    category,
                    blankToNull(companyName),
                    minPrice,
                    maxPrice,
                    startOfDay(fromDate),
                    endOfDay(toDate)
            );

            List<EventSummaryDTO> events = eventSearchService.searchEvents(request);
            if (events.isEmpty()) {
                return SearchResult.success(EMPTY_SEARCH_MESSAGE, events);
            }
            return SearchResult.success("Found " + events.size() + " event(s).", events);
        } catch (IllegalArgumentException ex) {
            return SearchResult.failure(ex.getMessage());
        } catch (RuntimeException ex) {
            logger.warn(SEARCH_FAILURE_MESSAGE, ex);
            return SearchResult.failure(SEARCH_FAILURE_MESSAGE);
        }
    }

    public MapResult loadEventMap(UUID eventId) {
        if (eventId == null) {
            return MapResult.failure("Select an event before loading the map.");
        }

        try {
            return eventQueryService.getEventMap(eventId)
                    .map(eventMap -> MapResult.success("Event map loaded.", eventMap))
                    .orElseGet(() -> MapResult.failure(MAP_NOT_FOUND_MESSAGE));
        } catch (RuntimeException ex) {
            logger.warn(MAP_FAILURE_MESSAGE, ex);
            return MapResult.failure(MAP_FAILURE_MESSAGE);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static java.time.Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private static java.time.Instant endOfDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1);
    }

    public record SearchResult(boolean success, String message, List<EventSummaryDTO> events) {

        public SearchResult {
            events = events == null ? List.of() : List.copyOf(events);
        }

        public static SearchResult success(String message, List<EventSummaryDTO> events) {
            return new SearchResult(true, message, events);
        }

        public static SearchResult failure(String message) {
            return new SearchResult(false, message, List.of());
        }

        public boolean empty() {
            return events.isEmpty();
        }
    }

    public record MapResult(boolean success, String message, EventMapDTO eventMap) {

        public static MapResult success(String message, EventMapDTO eventMap) {
            return new MapResult(true, message, eventMap);
        }

        public static MapResult failure(String message) {
            return new MapResult(false, message, null);
        }
    }
}
