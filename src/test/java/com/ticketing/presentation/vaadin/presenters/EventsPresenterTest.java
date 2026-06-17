package com.ticketing.presentation.vaadin.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import com.ticketing.application.SearchEventsRequest;
import com.ticketing.application.dto.CompanySummaryDTO;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.application.dto.LotteryRegistrationRequest;
import com.ticketing.application.dto.LotteryRegistrationResponse;
import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.EventService;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.LotteryRegistrationResult;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.MapResult;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.SearchResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;

@DisplayName("EventsPresenter")
@ExtendWith(VaadinSessionExtension.class)
class EventsPresenterTest {

    private EventService eventService;
    private CompanyService companyService;
    private EventsPresenter presenter;

    @BeforeEach
    void setUp() {
        eventService = mock(EventService.class);
        companyService = mock(CompanyService.class);
        presenter = new EventsPresenter(eventService, companyService);
    }

    @Test
    void GivenGuestOrMemberSession_WhenSearchingEvents_ThenApplicationSearchServiceIsCalledDirectly() {
        UUID memberId = UUID.randomUUID();
        EventSummaryDTO event = eventSummary("Spring Concert");
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(memberId);
        when(eventService.searchEvents(any(SearchEventsRequest.class))).thenReturn(List.of(event));

        SearchResult result = presenter.searchEvents(
                " spring ",
                "Tel Aviv",
                EventCategory.CONCERT,
                "Acme",
                new BigDecimal("10.00"),
                new BigDecimal("90.00"),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 30)
        );

        ArgumentCaptor<SearchEventsRequest> request = ArgumentCaptor.forClass(SearchEventsRequest.class);
        verify(eventService).searchEvents(request.capture());
        assertTrue(result.success());
        assertEquals(List.of(event), result.events());
        assertEquals("member-token", SessionContext.getSessionToken());
        assertEquals(memberId, SessionContext.getMemberId());
        assertEquals("spring", request.getValue().text());
        assertEquals("Tel Aviv", request.getValue().region());
        assertEquals(EventCategory.CONCERT, request.getValue().category());
        assertEquals("Acme", request.getValue().companyName());
    }

    @Test
    void GivenSearchReturnsEvents_WhenSearchingEvents_ThenResultsAreReturnedForVaadinComponents() {
        EventSummaryDTO first = eventSummary("Spring Concert");
        EventSummaryDTO second = eventSummary("Summer Festival");
        when(eventService.searchEvents(any(SearchEventsRequest.class))).thenReturn(List.of(first, second));

        SearchResult result = presenter.searchEvents(null, null, null, null, null, null, null, null);

        assertTrue(result.success());
        assertFalse(result.empty());
        assertEquals("Found 2 event(s).", result.message());
        assertEquals(List.of(first, second), result.events());
    }

    @Test
    void GivenSearchReturnsEmptyList_WhenSearchingEvents_ThenEmptyResultIsSuccessfulWithInfoMessage() {
        when(eventService.searchEvents(any(SearchEventsRequest.class))).thenReturn(List.of());

        SearchResult result = presenter.searchEvents("missing", null, null, null, null, null, null, null);

        assertTrue(result.success());
        assertTrue(result.empty());
        assertEquals("No events found for the current filters.", result.message());
    }

    @Test
    void GivenSelectedEventHasMap_WhenLoadingMap_ThenInventoryDataIsReturned() {
        UUID eventId = UUID.randomUUID();
        EventMapDTO eventMap = eventMap(eventId);
        when(eventService.getEventMap(eventId)).thenReturn(java.util.Optional.of(eventMap));

        MapResult result = presenter.loadEventMap(eventId);

        assertTrue(result.success());
        assertSame(eventMap, result.eventMap());
        assertEquals(2, result.eventMap().zones().size());
        assertEquals(10, result.eventMap().zones().get(0).availableCount());
        assertFalse(result.eventMap().zones().get(1).seats().get(1).available());
    }

    @Test
    void GivenMapIsMissing_WhenLoadingMap_ThenEventMapNotFoundMessageIsReturned() {
        UUID eventId = UUID.randomUUID();
        when(eventService.getEventMap(eventId)).thenReturn(java.util.Optional.empty());

        MapResult result = presenter.loadEventMap(eventId);

        assertFalse(result.success());
        assertNull(result.eventMap());
        assertEquals("Event map not found.", result.message());
    }

    @Test
    void GivenApplicationError_WhenSearchingEvents_ThenUserFacingErrorIsReturned() {
        when(eventService.searchEvents(any(SearchEventsRequest.class)))
                .thenThrow(new IllegalStateException("database internals"));

        SearchResult result = presenter.searchEvents(null, null, null, null, null, null, null, null);

        assertFalse(result.success());
        assertTrue(result.empty());
        assertEquals("Could not search events. Please try again.", result.message());
    }

    @Test
    void GivenInvalidPriceRange_WhenSearchingEvents_ThenSpecificValidationReasonIsReturned() {
        // An invalid filter (min > max) is rejected by SearchEventsRequest with a specific
        // IllegalArgumentException; the presenter surfaces that exact reason, not the generic message.
        SearchResult result = presenter.searchEvents(
                null, null, null, null,
                new BigDecimal("90.00"),
                new BigDecimal("10.00"),
                null, null
        );

        assertFalse(result.success());
        assertTrue(result.empty());
        assertEquals("minPrice (90.00) cannot be greater than maxPrice (10.00)", result.message());
    }

    @Test
    void GivenCompaniesExist_WhenSearchingCompanies_ThenApplicationServiceResultsAreReturned() {
        when(companyService.searchCompanies("ac")).thenReturn(List.of(new CompanySummaryDTO("Acme")));

        List<CompanySummaryDTO> results = presenter.searchCompanies("ac");

        assertEquals(List.of(new CompanySummaryDTO("Acme")), results);
    }

    @Test
    void GivenApplicationError_WhenSearchingCompanies_ThenEmptyListIsReturned() {
        when(companyService.searchCompanies(any())).thenThrow(new IllegalStateException("boom"));

        assertTrue(presenter.searchCompanies("x").isEmpty());
    }

    // ── Lottery registration tests ──────────────────────────────────

    @Test
    @DisplayName("Member session calls EventService.registerForLottery with the correct request")
    void GivenMemberSession_WhenRegisteringForLottery_ThenServiceIsCalledWithCorrectRequest() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        Instant now = Instant.now();
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        when(eventService.registerForLottery(any(), any(LotteryRegistrationRequest.class)))
                .thenReturn(LotteryRegistrationResponse.success(entryId, now));

        LotteryRegistrationResult result = presenter.registerForLottery(eventId, zoneId, 2);

        ArgumentCaptor<LotteryRegistrationRequest> captor = ArgumentCaptor.forClass(LotteryRegistrationRequest.class);
        verify(eventService).registerForLottery(any(), captor.capture());
        assertTrue(result.success());
        assertEquals(entryId, result.lotteryEntryId());
        assertEquals(now, result.registeredAt());
        assertEquals(eventId, captor.getValue().eventId());
        assertEquals(zoneId, captor.getValue().zoneId());
        assertEquals(2, captor.getValue().quantity());
    }

    @Test
    @DisplayName("No session is rejected before calling the service")
    void GivenNoSession_WhenRegisteringForLottery_ThenRejectsWithLoginMessage() {
        LotteryRegistrationResult result = presenter.registerForLottery(UUID.randomUUID(), UUID.randomUUID(), 1);

        assertFalse(result.success());
        assertTrue(result.message().toLowerCase().contains("logged in"));
    }

    @Test
    @DisplayName("Guest session is rejected with a members-only message")
    void GivenGuestSession_WhenRegisteringForLottery_ThenRejectsWithMembersOnlyMessage() {
        SessionContext.setSessionToken("guest-token");

        LotteryRegistrationResult result = presenter.registerForLottery(UUID.randomUUID(), UUID.randomUUID(), 1);

        assertFalse(result.success());
        assertTrue(result.message().toLowerCase().contains("members only"));
    }

    @Test
    @DisplayName("Duplicate-registration reason from service is preserved exactly")
    void GivenServiceReturnsDuplicateError_WhenRegisteringForLottery_ThenReasonIsPreserved() {
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        String reason = "Member is already registered for this lottery.";
        when(eventService.registerForLottery(any(), any(LotteryRegistrationRequest.class)))
                .thenReturn(LotteryRegistrationResponse.failure(reason));

        LotteryRegistrationResult result = presenter.registerForLottery(UUID.randomUUID(), UUID.randomUUID(), 1);

        assertFalse(result.success());
        assertEquals(reason, result.message());
    }

    @Test
    @DisplayName("Closed-window reason from service is preserved exactly")
    void GivenServiceReturnsClosedWindowError_WhenRegisteringForLottery_ThenReasonIsPreserved() {
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        String reason = "Lottery registration window is closed.";
        when(eventService.registerForLottery(any(), any(LotteryRegistrationRequest.class)))
                .thenReturn(LotteryRegistrationResponse.failure(reason));

        LotteryRegistrationResult result = presenter.registerForLottery(UUID.randomUUID(), UUID.randomUUID(), 1);

        assertFalse(result.success());
        assertEquals(reason, result.message());
    }

    @Test
    @DisplayName("Invalid-zone reason from service is preserved exactly")
    void GivenServiceReturnsInvalidZoneError_WhenRegisteringForLottery_ThenReasonIsPreserved() {
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        String reason = "Zone not found.";
        when(eventService.registerForLottery(any(), any(LotteryRegistrationRequest.class)))
                .thenReturn(LotteryRegistrationResponse.failure(reason));

        LotteryRegistrationResult result = presenter.registerForLottery(UUID.randomUUID(), UUID.randomUUID(), 1);

        assertFalse(result.success());
        assertEquals(reason, result.message());
    }

    @Test
    @DisplayName("Unexpected exception produces a safe generic message without stack traces")
    void GivenUnexpectedException_WhenRegisteringForLottery_ThenSafeGenericMessageIsReturned() {
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        when(eventService.registerForLottery(any(), any(LotteryRegistrationRequest.class)))
                .thenThrow(new RuntimeException("unexpected internal error"));

        LotteryRegistrationResult result = presenter.registerForLottery(UUID.randomUUID(), UUID.randomUUID(), 1);

        assertFalse(result.success());
        assertFalse(result.message().contains("unexpected internal error"));
        assertTrue(result.message().toLowerCase().contains("could not register") || result.message().toLowerCase().contains("please try again"));
    }

    private static EventSummaryDTO eventSummary(String name) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new EventSummaryDTO(
                UUID.randomUUID(),
                name,
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS)),
                EventStatus.PUBLISHED
        );
    }

    private static EventMapDTO eventMap(UUID eventId) {
        UUID floorId = UUID.randomUUID();
        UUID balconyId = UUID.randomUUID();
        return new EventMapDTO(
                eventId,
                "Spring Concert",
                "Acme",
                EventStatus.PUBLISHED,
                Map.of("Floor", floorId, "Balcony", balconyId),
                List.of(
                        new EventMapDTO.ZoneInfo(
                                floorId,
                                "Floor",
                                ZoneType.GENERAL_ADMISSION,
                                new BigDecimal("45.00"),
                                100,
                                10,
                                90,
                                List.of()
                        ),
                        new EventMapDTO.ZoneInfo(
                                balconyId,
                                "Balcony",
                                ZoneType.ASSIGNED_SEATING,
                                new BigDecimal("95.00"),
                                null,
                                null,
                                null,
                                List.of(
                                        new EventMapDTO.SeatInfo(UUID.randomUUID(), "A", "1", true),
                                        new EventMapDTO.SeatInfo(UUID.randomUUID(), "A", "2", false)
                                )
                        )
                )
        );
    }
}
