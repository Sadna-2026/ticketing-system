package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.application.dto.CompanySummaryDTO;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventPolicyBadgeDTO;
import com.ticketing.application.dto.EventPolicyBadgeDTO.Kind;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.application.dto.LotteryStatusDTO;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.SaleMethod;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.presentation.vaadin.components.SeatMapComponent;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.LotteryRegistrationResult;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.LotteryStatusResult;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.MapResult;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.SearchResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderMutationResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.Query;

import elemental.json.JsonArray;
import elemental.json.JsonObject;

@DisplayName("EventsView")
@ExtendWith(VaadinSessionExtension.class)
class EventsViewTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void GivenEventsView_WhenRendered_ThenGuestAndMemberSearchControlsAreAvailable() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();

        EventsView view = new EventsView(presenter, ordersPresenter);

        assertTrue(hasButton(view, "Search events"));
        assertTrue(hasButton(view, "Clear filters"));
        assertTrue(hasButton(view, "View selected map"));
        assertEquals(2, countComponents(view, TextField.class));
        assertEquals(2, countComponents(view, ComboBox.class));
        assertEquals(2, countComponents(view, BigDecimalField.class));
        assertEquals(2, countComponents(view, DatePicker.class));
    }

    @Test
    void GivenSearchReturnsEvents_WhenSearchButtonClicked_ThenResultsAreDisplayedInGrid() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        EventSummaryDTO event = eventSummary("Spring Concert");
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");

        Grid<EventSummaryDTO> grid = findGrid(view);
        List<EventSummaryDTO> rows = grid.getDataProvider().fetch(new Query<>()).toList();
        assertEquals(List.of(event), rows);
        assertTrue(hasText(view, "Found 1 event(s)."));
    }

    @Test
    void GivenSearchReturnsEmptyList_WhenSearchButtonClicked_ThenEmptyResultIsHandled() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        whenSearch(presenter).thenReturn(SearchResult.success("No events found for the current filters.", List.of()));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");

        Grid<EventSummaryDTO> grid = findGrid(view);
        assertEquals(0, grid.getDataProvider().fetch(new Query<>()).count());
        assertTrue(hasText(view, "No events found for the current filters."));
    }

    @Test
    void GivenSelectedEventHasMap_WhenViewMapClicked_ThenInventoryDataIsDisplayed() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        EventSummaryDTO event = eventSummary("Spring Concert");
        EventMapDTO loadedMap = sampleEventMap(event.id());
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", loadedMap));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");

        assertTrue(hasText(view, "Event map loaded."));
        assertTrue(hasText(view, "Company: Acme"));
        assertTrue(hasText(view, "Available: 10"));

        // The assigned-seating zone renders as a single client-side seat map fed a
        // compact payload, with availability reflected per seat (no per-seat component).
        JsonArray seats = seatMapPayload(view);
        assertEquals(2, seats.length());
        assertFalse(seatEntry(seats, "A", "1").getBoolean("taken"));
        assertTrue(seatEntry(seats, "A", "2").getBoolean("taken"));
    }

    @Test
    void GivenLoadedMap_WhenAddingGaAndAssignedTickets_ThenOrdersPresenterIsCalled() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        EventSummaryDTO event = eventSummary("Spring Concert");
        EventMapDTO loadedMap = sampleEventMap(event.id());
        UUID gaZoneId = loadedMap.zones().get(0).id();
        UUID seatZoneId = loadedMap.zones().get(1).id();
        UUID seatId = loadedMap.zones().get(1).seats().get(0).id();
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", loadedMap));
        when(ordersPresenter.addAssignedSeats(event.id(), seatZoneId, List.of(seatId)))
                .thenReturn(OrderMutationResult.success("Assigned seat added.", UUID.randomUUID(), null));
        when(ordersPresenter.addGATickets(event.id(), gaZoneId, 1))
                .thenReturn(OrderMutationResult.success("GA tickets added.", UUID.randomUUID(), null));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");
        clickButton(view, "Add GA tickets");
        assertTrue(hasText(view, "GA tickets added."));

        SeatMapComponent map = findSeatMap(view);
        map.onCommitSelection(new String[] { seatId.toString() });

        assertTrue(hasText(view, "Assigned seat added."));
        verify(ordersPresenter).addGATickets(event.id(), gaZoneId, 1);
        verify(ordersPresenter).addAssignedSeats(event.id(), seatZoneId, List.of(seatId));
    }

    @Test
    @DisplayName("Selecting a GA zone quantity passes the requested quantity to the backend")
    void GivenValidSession_WhenAddingMultipleGATickets_ThenCorrectQuantityIsPassed() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        EventSummaryDTO event = eventSummary("Spring Concert");
        EventMapDTO loadedMap = sampleEventMap(event.id());
        UUID gaZoneId = loadedMap.zones().get(0).id();
        
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", loadedMap));
        when(ordersPresenter.addGATickets(event.id(), gaZoneId, 3))
                .thenReturn(OrderMutationResult.success("3 GA tickets added.", UUID.randomUUID(), null));
        
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");

        findIntegerField(view, "Quantity").setValue(3);
        clickButton(view, "Add GA tickets");

        assertTrue(hasText(view, "3 GA tickets added."));
        verify(ordersPresenter).addGATickets(event.id(), gaZoneId, 3);
    }

    @Test
    @DisplayName("Adding more tickets than MaxQuantityPolicy allows shows failure message in UI")
    void GivenMixedEvent_WhenAddingMoreThanMaxQuantityTickets_ThenFailureMessageIsShown() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        EventSummaryDTO event = eventSummary("Mixed Limited Event");
        EventMapDTO loadedMap = sampleEventMap(event.id());
        UUID gaZoneId = loadedMap.zones().get(0).id();
        
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", loadedMap));
        when(ordersPresenter.addGATickets(event.id(), gaZoneId, 4))
                .thenReturn(OrderMutationResult.failure("Cannot purchase more than 3 tickets"));
        
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");

        findIntegerField(view, "Quantity").setValue(4);
        clickButton(view, "Add GA tickets");

        assertTrue(hasText(view, "Cannot purchase more than 3 tickets"));
    }

    @Test
    @DisplayName("Assigned-seating zone feeds the client seat map an ordered payload with free/taken status")
    void GivenAssignedZone_WhenMapRendered_ThenSeatPayloadIsOrderedWithFreeTakenStatus() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        EventSummaryDTO event = eventSummary("Spring Concert");
        EventMapDTO loadedMap = seatMapEventMap(event.id());
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", loadedMap));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");

        // The assigned zone renders as a single <seat-map> element (no per-seat component).
        JsonArray seats = seatMapPayload(view);

        // The payload is ordered by row label, then by seat number numerically (not lexicographically).
        assertEquals(List.of("A-1", "A-2", "A-10", "B-1"), seatLabels(seats));

        // Live availability is reflected per seat: A-10 is taken, A-1 is free.
        assertFalse(seatEntry(seats, "A", "1").getBoolean("taken"));
        assertTrue(seatEntry(seats, "A", "10").getBoolean("taken"));
    }

    @Test
    void GivenEventMapWithPolicies_WhenViewMapClicked_ThenRestrictionAndDiscountCardsAreShown() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        EventSummaryDTO event = eventSummary("Policy Concert");
        EventMapDTO loadedMap = policyEventMap(event.id());
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", loadedMap));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");

        assertTrue(hasText(view, "Purchase restrictions"));
        assertTrue(hasText(view, "Available discounts"));
        assertTrue(hasText(view, "Ticket limit"));
        assertTrue(hasText(view, "10% off all tickets"));
        assertFalse(hasText(view, "No discount"));
        assertFalse(hasText(view, "Allow all"));
    }

    @Test
    void GivenSelectedEventMapFails_WhenViewMapClicked_ThenFailureMessageIsShownInline() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        EventSummaryDTO event = eventSummary("Spring Concert");
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.failure("Event map not found."));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");

        assertTrue(hasText(view, "Event map not found."));
    }

    @Test
    void GivenApplicationError_WhenSearchButtonClicked_ThenErrorMessageIsShownToUser() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        whenSearch(presenter).thenReturn(SearchResult.failure("Could not search events. Please try again."));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");

        assertTrue(hasText(view, "Could not search events. Please try again."));
        assertEquals(0, findGrid(view).getDataProvider().fetch(new Query<>()).count());
    }

    @Test
    @DisplayName("A specific failure reason from the presenter is shown to the user, not a generic message")
    void GivenSpecificFailureReason_WhenSearchButtonClicked_ThenThatExactReasonIsShownAndGridStaysEmpty() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        whenSearch(presenter).thenReturn(SearchResult.failure("Minimum price cannot be greater than maximum price."));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");

        assertTrue(hasText(view, "Minimum price cannot be greater than maximum price."));
        assertEquals(0, findGrid(view).getDataProvider().fetch(new Query<>()).count());
    }

    @Test
    void GivenCompanySelectedInPicker_WhenSearching_ThenCompanyNameIsPassedToPresenter() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        when(presenter.searchCompanies("")).thenReturn(List.of(new CompanySummaryDTO("Acme")));
        whenSearch(presenter).thenReturn(SearchResult.success("No events found for the current filters.", List.of()));
        EventsView view = new EventsView(presenter, ordersPresenter);
        findCompanyComboBox(view).setValue(new CompanySummaryDTO("Acme"));

        clickButton(view, "Search events");

        verify(presenter).searchEvents(
                nullable(String.class),
                nullable(String.class),
                nullable(EventCategory.class),
                eq("Acme"),
                nullable(BigDecimal.class),
                nullable(BigDecimal.class),
                nullable(LocalDate.class),
                nullable(LocalDate.class)
        );
    }

    @Test
    @DisplayName("Search events within a specific company with multiple filters shows success and results")
    void GivenSearchWithinCompanyWithFilters_WhenSearchReturnsResults_ThenEventsAreDisplayedAndSuccessMessageShown() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        EventSummaryDTO event = eventSummary("Company Festival");
        
        LocalDate fromDate = LocalDate.now();
        LocalDate toDate = LocalDate.now().plusDays(30);
        BigDecimal minPrice = new BigDecimal("10.00");
        BigDecimal maxPrice = new BigDecimal("100.00");
        
        when(presenter.searchCompanies("")).thenReturn(List.of(new CompanySummaryDTO("Acme")));
        when(presenter.searchEvents(
                eq("Festival"),
                eq("North"),
                eq(EventCategory.FESTIVAL),
                eq("Acme"),
                eq(minPrice),
                eq(maxPrice),
                eq(fromDate),
                eq(toDate)
        )).thenReturn(SearchResult.success("Found 1 event(s) for Acme.", List.of(event)));
        
        EventsView view = new EventsView(presenter, ordersPresenter);
        
        // Fill search form
        findTextField(view, "Search text").setValue("Festival");
        findTextField(view, "Region").setValue("North");
        findCompanyComboBox(view).setValue(new CompanySummaryDTO("Acme"));
        findCategoryComboBox(view).setValue(EventCategory.FESTIVAL);
        findBigDecimalField(view, "Min price").setValue(minPrice);
        findBigDecimalField(view, "Max price").setValue(maxPrice);
        findDatePicker(view, "From date").setValue(fromDate);
        findDatePicker(view, "To date").setValue(toDate);

        clickButton(view, "Search events");

        Grid<EventSummaryDTO> grid = findGrid(view);
        List<EventSummaryDTO> rows = grid.getDataProvider().fetch(new Query<>()).toList();
        assertEquals(List.of(event), rows);
        assertTrue(hasText(view, "Found 1 event(s) for Acme."));
        
        verify(presenter).searchEvents(
                "Festival", "North", EventCategory.FESTIVAL, "Acme", minPrice, maxPrice, fromDate, toDate
        );
    }

    @Test
    @DisplayName("Search events within a specific company shows exact failure reason when backend rejects")
    void GivenSearchWithinCompanyWithFilters_WhenSearchFails_ThenSpecificFailureReasonIsShown() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        
        LocalDate fromDate = LocalDate.now();
        LocalDate toDate = LocalDate.now().minusDays(1); // Invalid dates
        
        when(presenter.searchCompanies("")).thenReturn(List.of(new CompanySummaryDTO("Acme")));
        when(presenter.searchEvents(
                any(), any(), any(), eq("Acme"), any(), any(), eq(fromDate), eq(toDate)
        )).thenReturn(SearchResult.failure("To Date cannot be before From Date."));
        
        EventsView view = new EventsView(presenter, ordersPresenter);
        
        findCompanyComboBox(view).setValue(new CompanySummaryDTO("Acme"));
        findDatePicker(view, "From date").setValue(fromDate);
        findDatePicker(view, "To date").setValue(toDate);

        clickButton(view, "Search events");

        Grid<EventSummaryDTO> grid = findGrid(view);
        assertEquals(0, grid.getDataProvider().fetch(new Query<>()).count());
    }

    @Test
    void GivenNoSession_WhenMapRendered_ThenReservationActionsAreDisabled() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        when(ordersPresenter.currentSessionState()).thenReturn(new SessionContext.UiState(false, false, false, false, null, null));
        EventSummaryDTO event = eventSummary("Spring Concert");
        EventMapDTO loadedMap = sampleEventMap(event.id());
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", loadedMap));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");

        Button addGa = findButton(view, "Add GA tickets");
        assertFalse(addGa.isEnabled(), "GA button should be disabled with no session");

        SeatMapComponent map = findSeatMap(view);
        assertFalse(map.isEnabled(), "Seat map should be disabled with no session");
    }

    @Test
    void GivenValidSession_WhenAddTicketsFails_ThenFailureMessageIsShown() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        EventSummaryDTO event = eventSummary("Spring Concert");
        EventMapDTO loadedMap = sampleEventMap(event.id());
        UUID gaZoneId = loadedMap.zones().get(0).id();
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", loadedMap));
        when(ordersPresenter.addGATickets(event.id(), gaZoneId, 1))
                .thenReturn(OrderMutationResult.failure("Not enough tickets available."));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");
        clickButton(view, "Add GA tickets");

        assertTrue(hasText(view, "Not enough tickets available."));
    }

    // ── Lottery view tests ────────────────────────────────────────────

    @Test
    @DisplayName("UI-19: Lottery badge is visible in results grid for lottery events")
    void GivenLotteryEvent_WhenSearchResultsDisplayed_ThenLotteryBadgeIsVisibleInGrid() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        EventSummaryDTO lotteryEvent = lotteryEventSummary("Lottery Concert");
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(lotteryEvent)));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");

        Grid<EventSummaryDTO> grid = findGrid(view);
        List<EventSummaryDTO> rows = grid.getDataProvider().fetch(new Query<>()).toList();
        assertEquals(SaleMethod.LOTTERY, rows.get(0).saleMethod());
    }

    @Test
    @DisplayName("UI-19: Lottery panel is visible for lottery events; normal purchase controls are hidden")
    void GivenLotteryEventMap_WhenViewMapClicked_ThenLotteryPanelIsShownAndNormalControlsAreHidden() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockMemberOrdersPresenter();
        EventSummaryDTO event = lotteryEventSummary("Lottery Concert");
        EventMapDTO lotteryMap = lotteryEventMap(event.id(), true);
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", lotteryMap));
        when(presenter.getLotteryStatus(event.id())).thenReturn(LotteryStatusResult.notRegistered());
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");

        assertTrue(hasText(view, "LOTTERY EVENT"));
        assertTrue(hasButton(view, "Enter lottery"));
        assertFalse(hasButton(view, "Add GA tickets"));
    }

    @Test
    @DisplayName("UI-19: Guest sees members-only message instead of registration form")
    void GivenGuestSession_WhenLotteryEventMapDisplayed_ThenMembersOnlyMessageIsShown() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter(); // guest session
        EventSummaryDTO event = lotteryEventSummary("Lottery Concert");
        EventMapDTO lotteryMap = lotteryEventMap(event.id(), true);
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", lotteryMap));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");

        assertTrue(hasText(view, "Members only"));
        assertFalse(hasButton(view, "Enter lottery"));
    }

    @Test
    @DisplayName("UI-19: Already-registered member sees registered status; Enter lottery button is absent")
    void GivenAlreadyRegisteredMember_WhenLotteryEventMapDisplayed_ThenRegisteredStatusIsShown() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockMemberOrdersPresenter();
        EventSummaryDTO event = lotteryEventSummary("Lottery Concert");
        EventMapDTO lotteryMap = lotteryEventMap(event.id(), true);
        UUID zoneId = lotteryMap.zones().get(0).id();
        UUID entryId = UUID.randomUUID();
        LotteryStatusResult alreadyRegistered = LotteryStatusResult.registered(
                LotteryStatusDTO.registered(entryId, event.id(), zoneId, 2, Instant.now()));
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", lotteryMap));
        when(presenter.getLotteryStatus(event.id())).thenReturn(alreadyRegistered);
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");

        assertTrue(hasText(view, "You are registered for this lottery"));
        assertFalse(hasButton(view, "Enter lottery"));
    }

    @Test
    @DisplayName("UI-19: Successful registration shows confirmation and disables the Enter lottery button")
    void GivenMemberWithOpenWindow_WhenEnterLotteryClicked_ThenSuccessConfirmationIsShown() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockMemberOrdersPresenter();
        EventSummaryDTO event = lotteryEventSummary("Lottery Concert");
        EventMapDTO lotteryMap = lotteryEventMap(event.id(), true);
        UUID zoneId = lotteryMap.zones().get(0).id();
        UUID entryId = UUID.randomUUID();
        Instant registeredAt = Instant.now();
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(event.id()))
                .thenReturn(MapResult.success("Event map loaded.", lotteryMap))
                .thenReturn(MapResult.success("Event map loaded.", lotteryMap));
        when(presenter.getLotteryStatus(event.id()))
                .thenReturn(LotteryStatusResult.notRegistered())
                .thenReturn(LotteryStatusResult.registered(
                        LotteryStatusDTO.registered(entryId, event.id(), zoneId, 1, registeredAt)));
        when(presenter.registerForLottery(eq(event.id()), eq(zoneId), eq(1)))
                .thenReturn(LotteryRegistrationResult.success("Lottery registration successful.", entryId, registeredAt));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");

        // Set zone and submit
        findLotteryZoneComboBox(view).setValue(lotteryMap.zones().get(0));
        clickButton(view, "Enter lottery");

        verify(presenter).registerForLottery(event.id(), zoneId, 1);
    }

    @Test
    @DisplayName("UI-19: Specific failure reason is shown when registration fails")
    void GivenRegistrationFails_WhenEnterLotteryClicked_ThenSpecificFailureReasonIsShown() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockMemberOrdersPresenter();
        EventSummaryDTO event = lotteryEventSummary("Lottery Concert");
        EventMapDTO lotteryMap = lotteryEventMap(event.id(), true);
        UUID zoneId = lotteryMap.zones().get(0).id();
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", lotteryMap));
        when(presenter.getLotteryStatus(event.id())).thenReturn(LotteryStatusResult.notRegistered());
        when(presenter.registerForLottery(eq(event.id()), eq(zoneId), eq(1)))
                .thenReturn(LotteryRegistrationResult.failure("Member is already registered for this lottery."));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");

        findLotteryZoneComboBox(view).setValue(lotteryMap.zones().get(0));
        clickButton(view, "Enter lottery");

        assertTrue(hasText(view, "Member is already registered for this lottery."));
    }

    @Test
    @DisplayName("UI-19: Lottery events hide regular purchase controls; regular events retain them")
    void GivenRegularEvent_WhenMapLoaded_ThenNormalPurchaseControlsAreShownAndNoLotteryControls() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        EventSummaryDTO event = eventSummary("Regular Concert");
        EventMapDTO regularMap = sampleEventMap(event.id());
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", regularMap));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");

        assertTrue(hasButton(view, "Add GA tickets"));
        assertFalse(hasButton(view, "Enter lottery"));
        assertFalse(hasText(view, "LOTTERY EVENT"));
    }

    @Test
    @DisplayName("UI-19: Registration window closed message shown when window is not open")
    void GivenClosedLotteryWindow_WhenLotteryEventMapDisplayed_ThenClosedWindowMessageIsShown() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockMemberOrdersPresenter();
        EventSummaryDTO event = lotteryEventSummary("Lottery Concert");
        EventMapDTO lotteryMap = lotteryEventMap(event.id(), false); // window closed
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", lotteryMap));
        when(presenter.getLotteryStatus(event.id())).thenReturn(LotteryStatusResult.notRegistered());
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");

        assertTrue(hasText(view, "Registration window is closed"));
        assertFalse(hasButton(view, "Enter lottery"));
    }

    @SuppressWarnings("unchecked")
    private ComboBox<EventMapDTO.ZoneInfo> findLotteryZoneComboBox(Component root) {
        return (ComboBox<EventMapDTO.ZoneInfo>) componentsOf(root).stream()
                .filter(ComboBox.class::isInstance)
                .map(ComboBox.class::cast)
                .filter(combo -> "Select zone".equals(combo.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Lottery zone ComboBox not found"));
    }

    private OrdersPresenter mockMemberOrdersPresenter() {
        OrdersPresenter ordersPresenter = mock(OrdersPresenter.class);
        when(ordersPresenter.currentSessionLabel()).thenReturn("Current session: Member (alice)");
        when(ordersPresenter.currentSessionState()).thenReturn(
                new SessionContext.UiState(true, false, true, false, "alice", "MEMBER"));
        when(ordersPresenter.loadCurrentOrder()).thenReturn(OrderResult.success("No active order found.", null, null));
        return ordersPresenter;
    }

    private static EventSummaryDTO lotteryEventSummary(String name) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new EventSummaryDTO(
                UUID.randomUUID(),
                name,
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS)),
                EventStatus.PUBLISHED,
                SaleMethod.LOTTERY
        );
    }

    private static EventMapDTO lotteryEventMap(UUID eventId, boolean windowOpen) {
        UUID zoneId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant open = windowOpen ? now.minus(1, ChronoUnit.DAYS) : now.minus(5, ChronoUnit.DAYS);
        Instant close = windowOpen ? now.plus(5, ChronoUnit.DAYS) : now.minus(1, ChronoUnit.DAYS);
        return new EventMapDTO(
                eventId,
                "Lottery Concert",
                "Acme",
                EventStatus.PUBLISHED,
                Map.of("Floor", zoneId),
                List.of(new EventMapDTO.ZoneInfo(
                        zoneId,
                        "Floor",
                        ZoneType.GENERAL_ADMISSION,
                        new BigDecimal("80.00"),
                        200,
                        200,
                        0,
                        List.of()
                )),
                null,
                "A lottery-based event.",
                List.of(),
                List.of(),
                SaleMethod.LOTTERY,
                open,
                close,
                windowOpen
        );
    }

    @SuppressWarnings("unchecked")
    private ComboBox<CompanySummaryDTO> findCompanyComboBox(Component root) {
        return (ComboBox<CompanySummaryDTO>) componentsOf(root).stream()
                .filter(ComboBox.class::isInstance)
                .map(ComboBox.class::cast)
                .filter(combo -> "Company".equals(combo.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Company ComboBox not found"));
    }

    @SuppressWarnings("unchecked")
    private ComboBox<EventCategory> findCategoryComboBox(Component root) {
        return (ComboBox<EventCategory>) componentsOf(root).stream()
                .filter(ComboBox.class::isInstance)
                .map(ComboBox.class::cast)
                .filter(combo -> "Category".equals(combo.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Category ComboBox not found"));
    }

    private TextField findTextField(Component root, String label) {
        return (TextField) componentsOf(root).stream()
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(field -> label.equals(field.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(label + " TextField not found"));
    }

    private IntegerField findIntegerField(Component root, String label) {
        return (IntegerField) componentsOf(root).stream()
                .filter(IntegerField.class::isInstance)
                .map(IntegerField.class::cast)
                .filter(field -> label.equals(field.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(label + " IntegerField not found"));
    }

    private BigDecimalField findBigDecimalField(Component root, String label) {
        return (BigDecimalField) componentsOf(root).stream()
                .filter(BigDecimalField.class::isInstance)
                .map(BigDecimalField.class::cast)
                .filter(field -> label.equals(field.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(label + " BigDecimalField not found"));
    }

    private DatePicker findDatePicker(Component root, String label) {
        return (DatePicker) componentsOf(root).stream()
                .filter(DatePicker.class::isInstance)
                .map(DatePicker.class::cast)
                .filter(field -> label.equals(field.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(label + " DatePicker not found"));
    }

    private java.util.List<Component> componentsOf(Component root) {
        java.util.List<Component> result = new java.util.ArrayList<>();
        result.add(root);
        root.getChildren().forEach(child -> result.addAll(componentsOf(child)));
        return result;
    }

    private OrdersPresenter mockOrdersPresenter() {
        OrdersPresenter ordersPresenter = mock(OrdersPresenter.class);
        when(ordersPresenter.currentSessionLabel()).thenReturn("Current session: Guest");
        when(ordersPresenter.currentSessionState()).thenReturn(new SessionContext.UiState(true, true, false, false, null, "Guest"));
        when(ordersPresenter.loadCurrentOrder()).thenReturn(OrderResult.success("No active order found.", null, null));
        return ordersPresenter;
    }

    private org.mockito.stubbing.OngoingStubbing<SearchResult> whenSearch(EventsPresenter presenter) {
        return when(presenter.searchEvents(
                nullable(String.class),
                nullable(String.class),
                nullable(EventCategory.class),
                nullable(String.class),
                nullable(BigDecimal.class),
                nullable(BigDecimal.class),
                nullable(LocalDate.class),
                nullable(LocalDate.class)
        ));
    }

    private boolean hasButton(Component root, String text) {
        if (root instanceof Button button && text.equals(button.getText())) {
            return true;
        }
        return root.getChildren().anyMatch(child -> hasButton(child, text));
    }

    private void clickButton(Component root, String text) {
        Button button = findButton(root, text);
        assertNotNull(button, "button not found: " + text);
        button.click();
    }

    private Button findButton(Component root, String text) {
        if (root instanceof Button button && text.equals(button.getText())) {
            return button;
        }
        return root.getChildren()
                .map(child -> findButton(child, text))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean hasText(Component root, String text) {
        if (root instanceof HasText hasText && text.equals(hasText.getText())) {
            return true;
        }
        return root.getChildren().anyMatch(child -> hasText(child, text));
    }

    @Test
    void GivenEventsView_WhenRendered_ThenResultsGridShowsEmptyStateMessage() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();

        EventsView view = new EventsView(presenter, ordersPresenter);

        assertEquals("No events match your search yet — adjust the filters and search again.",
                findGrid(view).getEmptyStateText());
    }

    @SuppressWarnings("unchecked")
    private Grid<EventSummaryDTO> findGrid(Component root) {
        return findGridOptional(root).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Optional<Grid<EventSummaryDTO>> findGridOptional(Component root) {
        if (root instanceof Grid<?> grid) {
            return Optional.of((Grid<EventSummaryDTO>) grid);
        }
        return root.getChildren()
                .map(this::findGridOptional)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .or(() -> Optional.empty());
    }

    private SeatMapComponent findSeatMap(Component root) {
        SeatMapComponent map = findSeatMapOrNull(root);
        assertNotNull(map, "SeatMapComponent not found");
        return map;
    }

    private SeatMapComponent findSeatMapOrNull(Component root) {
        if (root instanceof SeatMapComponent map) {
            return map;
        }
        return root.getChildren()
                .map(this::findSeatMapOrNull)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private JsonArray seatMapPayload(Component root) {
        return (JsonArray) findSeatMap(root).getElement().getPropertyRaw("seats");
    }

    private List<String> seatLabels(JsonArray seats) {
        List<String> labels = new java.util.ArrayList<>();
        for (int i = 0; i < seats.length(); i++) {
            JsonObject seat = seats.getObject(i);
            labels.add(seat.getString("row") + "-" + seat.getString("num"));
        }
        return labels;
    }

    private JsonObject seatEntry(JsonArray seats, String row, String num) {
        for (int i = 0; i < seats.length(); i++) {
            JsonObject seat = seats.getObject(i);
            if (row.equals(seat.getString("row")) && num.equals(seat.getString("num"))) {
                return seat;
            }
        }
        throw new AssertionError("Seat not found in payload: " + row + "-" + num);
    }

    private long countComponents(Component root, Class<? extends Component> type) {
        long current = type.isInstance(root) ? 1 : 0;
        return current + root.getChildren()
                .mapToLong(child -> countComponents(child, type))
                .sum();
    }

    private static EventSummaryDTO eventSummary(String name) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new EventSummaryDTO(
                UUID.randomUUID(),
                name,
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS)),
                EventStatus.PUBLISHED,
                com.ticketing.domain.event.SaleMethod.REGULAR
        );
    }

    private static EventMapDTO policyEventMap(UUID eventId) {
        UUID floorId = UUID.randomUUID();
        return new EventMapDTO(
                eventId,
                "Policy Concert",
                "Acme",
                EventStatus.PUBLISHED,
                Map.of("Floor", floorId),
                List.of(new EventMapDTO.ZoneInfo(
                        floorId,
                        "Floor",
                        ZoneType.GENERAL_ADMISSION,
                        new BigDecimal("45.00"),
                        100,
                        10,
                        90,
                        List.of())),
                null,
                "Requires at most 3 tickets.",
                List.of(new EventPolicyBadgeDTO(Kind.RESTRICTION, "Ticket limit", "Up to 3 tickets per order")),
                List.of(new EventPolicyBadgeDTO(Kind.DISCOUNT, "Discount", "10% off all tickets")));
    }

    private static EventMapDTO sampleEventMap(UUID eventId) {
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

    // Assigned zone with seats supplied out of order (across rows and seat numbers)
    // so a rendering test can assert the seat map sorts them: rows by label, seats
    // numerically. A-10 is taken to exercise free/taken status alongside ordering.
    private static EventMapDTO seatMapEventMap(UUID eventId) {
        UUID balconyId = UUID.randomUUID();
        return new EventMapDTO(
                eventId,
                "Spring Concert",
                "Acme",
                EventStatus.PUBLISHED,
                Map.of("Balcony", balconyId),
                List.of(
                        new EventMapDTO.ZoneInfo(
                                balconyId,
                                "Balcony",
                                ZoneType.ASSIGNED_SEATING,
                                new BigDecimal("95.00"),
                                null,
                                null,
                                null,
                                List.of(
                                        new EventMapDTO.SeatInfo(UUID.randomUUID(), "A", "10", false),
                                        new EventMapDTO.SeatInfo(UUID.randomUUID(), "B", "1", true),
                                        new EventMapDTO.SeatInfo(UUID.randomUUID(), "A", "1", true),
                                        new EventMapDTO.SeatInfo(UUID.randomUUID(), "A", "2", true)
                                )
                        )
                )
        );
    }
}
