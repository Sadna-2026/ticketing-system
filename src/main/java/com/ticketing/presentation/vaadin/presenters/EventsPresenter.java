package com.ticketing.presentation.vaadin.presenters;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketing.application.SearchEventsRequest;
import com.ticketing.application.dto.CompanySummaryDTO;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.application.dto.LotteryRegistrationRequest;
import com.ticketing.application.dto.LotteryRegistrationResponse;
import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.EventService;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.presentation.vaadin.util.SessionContext;

@Component
public class EventsPresenter {

    private static final Logger logger = LoggerFactory.getLogger(EventsPresenter.class);

    private static final String EMPTY_SEARCH_MESSAGE = "No events found for the current filters.";
    private static final String SEARCH_FAILURE_MESSAGE = "Could not search events. Please try again.";
    private static final String MAP_NOT_FOUND_MESSAGE = "Event map not found.";
    private static final String MAP_FAILURE_MESSAGE = "Could not load event map. Please try again.";
    private static final String LOTTERY_REGISTRATION_FAILURE_MESSAGE =
            "Could not register for the lottery. Please try again.";

    private final EventService eventService;
    private final CompanyService companyService;

    public EventsPresenter(EventService eventService, CompanyService companyService) {
        this.eventService = eventService;
        this.companyService = companyService;
    }

    /** Active companies for the optional company filter; empty on failure. */
    public List<CompanySummaryDTO> searchCompanies(String query) {
        try {
            return companyService.searchCompanies(query);
        } catch (RuntimeException ex) {
            logger.warn("Company search failed", ex);
            return List.of();
        }
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

            List<EventSummaryDTO> events = eventService.searchEvents(request);
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
            return eventService.getEventMap(eventId)
                    .map(eventMap -> MapResult.success("Event map loaded.", eventMap))
                    .orElseGet(() -> MapResult.failure(MAP_NOT_FOUND_MESSAGE));
        } catch (RuntimeException ex) {
            logger.warn(MAP_FAILURE_MESSAGE, ex);
            return MapResult.failure(MAP_FAILURE_MESSAGE);
        }
    }

    public LotteryRegistrationResult registerForLottery(UUID eventId) {
        SessionContext.UiState state = SessionContext.currentUiState();
        if (state.noSession()) {
            return LotteryRegistrationResult.failure("You must be logged in to register for a lottery.");
        }
        if (state.guest()) {
            return LotteryRegistrationResult.failure("Lottery registration is for members only. Please log in as a member.");
        }
        String token = SessionContext.getSessionToken();
        if (token == null) {
            return LotteryRegistrationResult.failure("No active session found. Please log in again.");
        }
        if (eventId == null) {
            return LotteryRegistrationResult.failure("Event ID is required.");
        }
        try {
            LotteryRegistrationRequest request = new LotteryRegistrationRequest(eventId);
            LotteryRegistrationResponse response = eventService.registerForLottery(token, request);
            if (response.success()) {
                return LotteryRegistrationResult.success(response.message(), response.lotteryEntryId(), response.registeredAt());
            }
            return LotteryRegistrationResult.failure(response.message());
        } catch (RuntimeException ex) {
            return LotteryRegistrationResult.failure(userMessage(ex, LOTTERY_REGISTRATION_FAILURE_MESSAGE));
        }
    }

    public Optional<LotteryRegistrationResponse> getLotteryStatus(UUID eventId) {
        if (eventId == null) return Optional.empty();
        String token = SessionContext.getSessionToken();
        if (token == null) return Optional.empty();
        try {
            return eventService.getMemberLotteryEntry(token, eventId);
        } catch (RuntimeException ex) {
            logger.warn("Failed to get lottery status for eventId={}", eventId, ex);
            return Optional.empty();
        }
    }

    private String userMessage(RuntimeException ex, String fallback) {
        if (ex instanceof IllegalArgumentException
                || ex instanceof IllegalStateException
                || ex instanceof SecurityException) {
            String message = ex.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
        }

        logger.warn(fallback, ex);
        return fallback;
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

    public record LotteryRegistrationResult(
            boolean success,
            String message,
            UUID lotteryEntryId,
            Instant registeredAt
    ) {
        public static LotteryRegistrationResult success(String message, UUID lotteryEntryId, Instant registeredAt) {
            return new LotteryRegistrationResult(true, message, lotteryEntryId, registeredAt);
        }

        public static LotteryRegistrationResult failure(String message) {
            return new LotteryRegistrationResult(false, message, null, null);
        }
    }
}
