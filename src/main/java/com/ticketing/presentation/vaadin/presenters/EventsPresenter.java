package com.ticketing.presentation.vaadin.presenters;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
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
import com.ticketing.application.dto.LotteryStatusDTO;
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
    private static final String NO_MEMBER_SESSION_MESSAGE = "Log in as a member to register for the lottery.";
    private static final String LOTTERY_FAILURE_MESSAGE = "Could not register for the lottery. Please try again.";

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

    public LotteryRegistrationResult registerForLottery(UUID eventId, UUID zoneId, Integer quantity) {
        if (eventId == null) {
            return LotteryRegistrationResult.failure("Select an event before registering for the lottery.");
        }
        if (zoneId == null) {
            return LotteryRegistrationResult.failure("Select a zone before registering for the lottery.");
        }
        if (quantity == null || quantity <= 0) {
            return LotteryRegistrationResult.failure("Quantity must be positive.");
        }
        if (!SessionContext.isLoggedInMember()) {
            if (SessionContext.isGuestSession()) {
                return LotteryRegistrationResult.failure("Members only: log in as a member to register for the lottery.");
            }
            return LotteryRegistrationResult.failure(NO_MEMBER_SESSION_MESSAGE);
        }
        String token = sessionToken();
        if (token == null) {
            return LotteryRegistrationResult.failure(NO_MEMBER_SESSION_MESSAGE);
        }
        try {
            LotteryRegistrationRequest request = new LotteryRegistrationRequest(eventId, zoneId, quantity);
            LotteryRegistrationResponse response = eventService.registerForLottery(token, request);
            if (response.success()) {
                return LotteryRegistrationResult.success(response.message(), response.lotteryEntryId(), response.registeredAt());
            }
            return LotteryRegistrationResult.failure(response.message());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return LotteryRegistrationResult.failure(ex.getMessage());
        } catch (SecurityException ex) {
            return LotteryRegistrationResult.failure("Members only: " + ex.getMessage());
        } catch (RuntimeException ex) {
            logger.warn("Lottery registration failed: eventId={}, zoneId={}", eventId, zoneId, ex);
            return LotteryRegistrationResult.failure(LOTTERY_FAILURE_MESSAGE);
        }
    }

    public LotteryStatusResult getLotteryStatus(UUID eventId) {
        if (eventId == null) {
            return LotteryStatusResult.unknown("No event selected.");
        }
        String token = sessionToken();
        if (token == null) {
            return LotteryStatusResult.notRegistered();
        }
        try {
            return eventService.getLotteryStatus(token, eventId)
                    .map(dto -> dto.registered()
                            ? LotteryStatusResult.registered(dto)
                            : LotteryStatusResult.notRegistered())
                    .orElseGet(LotteryStatusResult::notRegistered);
        } catch (RuntimeException ex) {
            logger.warn("Could not load lottery status for event {}", eventId, ex);
            return LotteryStatusResult.unknown("Could not load lottery status. Please try again.");
        }
    }

    private static String sessionToken() {
        String token = SessionContext.getSessionToken();
        return token == null || token.isBlank() ? null : token;
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

    public record LotteryStatusResult(
            boolean registered,
            String message,
            UUID entryId,
            UUID zoneId,
            int quantity,
            Instant registeredAt
    ) {
        public static LotteryStatusResult registered(LotteryStatusDTO dto) {
            return new LotteryStatusResult(true, "You are registered for this lottery.",
                    dto.entryId(), dto.zoneId(), dto.quantity(), dto.registeredAt());
        }

        public static LotteryStatusResult notRegistered() {
            return new LotteryStatusResult(false, "Not registered.", null, null, 0, null);
        }

        public static LotteryStatusResult unknown(String message) {
            return new LotteryStatusResult(false, message, null, null, 0, null);
        }
    }
}
