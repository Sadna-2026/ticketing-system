package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.ticketing.application.dto.LotteryRegistrationResponse;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.LayoutCellType;
import com.ticketing.domain.event.SaleMethod;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.LotteryRegistrationResult;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.MapResult;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.SearchResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderMutationResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderResult;
import com.ticketing.presentation.vaadin.presenters.QueuePresenter;
import com.ticketing.presentation.vaadin.presenters.QueuePresenter.QueueResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.Query;

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

        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

        assertTrue(hasButton(view, "Search events"));
        assertTrue(hasButton(view, "Clear filters"));
        assertTrue(hasButton(view, "Select tickets"));
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
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

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
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

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
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "Select tickets");

        assertTrue(hasText(view, "Event map loaded."));
        assertTrue(hasText(view, "Company: Acme"));
        assertTrue(hasText(view, "Choose your tickets"));
        // Legend reflects live availability per zone.
        assertTrue(containsText(view, "avail 10/100"));
        assertTrue(containsText(view, "1 free"));
    }

    @Test
    void GivenViewedOneEvent_WhenSelectingAndViewingAnother_ThenSecondEventMapLoads() {
        // Regression: after viewing (and being admitted to) one event, selecting a different
        // event must load THAT event's map — not stay pinned to the first (which breaks badly
        // once the first event is cancelled and its map can no longer load).
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        EventSummaryDTO first = eventSummary("First Event");
        EventSummaryDTO second = eventSummary("Second Event");
        whenSearch(presenter).thenReturn(SearchResult.success("Found 2 event(s).", List.of(first, second)));
        when(presenter.loadEventMap(eq(first.id())))
                .thenReturn(MapResult.success("Event map loaded.", sampleEventMap(first.id())));
        when(presenter.loadEventMap(eq(second.id())))
                .thenReturn(MapResult.success("Event map loaded.", sampleEventMap(second.id())));
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(first);
        clickButton(view, "Select tickets");

        findGrid(view).asSingleSelect().setValue(second);
        clickButton(view, "Select tickets");

        verify(presenter).loadEventMap(second.id());
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
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "Select tickets");

        // GA: clicking a GA area opens a quantity popup; confirming adds to the cart.
        Dialog gaDialog = view.buildGaQuantityDialog(loadedMap.zones().get(0));
        clickButton(gaDialog, "Add to cart");
        assertTrue(hasText(view, "GA tickets added."));

        // Seats: select on the map, then add the staged selection.
        view.toggleSeat(seatId);
        view.addSelectedSeats();

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
        
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "Select tickets");

        Dialog gaDialog = view.buildGaQuantityDialog(loadedMap.zones().get(0));
        findIntegerField(gaDialog, "Quantity").setValue(3);
        clickButton(gaDialog, "Add to cart");

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
        
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "Select tickets");

        Dialog gaDialog = view.buildGaQuantityDialog(loadedMap.zones().get(0));
        findIntegerField(gaDialog, "Quantity").setValue(4);
        clickButton(gaDialog, "Add to cart");

        assertTrue(hasText(view, "Cannot purchase more than 3 tickets"));
    }

    @Test
    @DisplayName("Taken seats on the map cannot be added to the cart; free seats can")
    void GivenAssignedZone_WhenSeatTaken_ThenItIsNotSelectable() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        EventSummaryDTO event = eventSummary("Spring Concert");
        EventMapDTO loadedMap = seatMapEventMap(event.id());
        UUID takenSeat = loadedMap.zones().get(0).seats().stream()
                .filter(seat -> !seat.available()).findFirst().orElseThrow().id();
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", loadedMap));
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "Select tickets");

        // A taken seat isn't a selectable cell, so trying to stage it is a no-op.
        view.toggleSeat(takenSeat);
        view.addSelectedSeats();

        verify(ordersPresenter, never()).addAssignedSeats(any(), any(), any());
    }

    @Test
    void GivenEventMapWithPolicies_WhenViewMapClicked_ThenRestrictionAndDiscountCardsAreShown() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        EventSummaryDTO event = eventSummary("Policy Concert");
        EventMapDTO loadedMap = policyEventMap(event.id());
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", loadedMap));
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "Select tickets");

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
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "Select tickets");

        assertTrue(hasText(view, "Event map not found."));
    }

    @Test
    void GivenApplicationError_WhenSearchButtonClicked_ThenErrorMessageIsShownToUser() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();
        whenSearch(presenter).thenReturn(SearchResult.failure("Could not search events. Please try again."));
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

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
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

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
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());
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
        
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());
        
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
        
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());
        
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
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "Select tickets");

        assertTrue(containsText(view, "Start a guest or member session to select tickets."));
        // With no session, seat cells aren't wired for selection, so staging is a no-op.
        UUID seatId = loadedMap.zones().get(1).seats().get(0).id();
        view.toggleSeat(seatId);
        view.addSelectedSeats();
        verify(ordersPresenter, never()).addAssignedSeats(any(), any(), any());
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
        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "Select tickets");

        Dialog gaDialog = view.buildGaQuantityDialog(loadedMap.zones().get(0));
        clickButton(gaDialog, "Add to cart");

        assertTrue(hasText(view, "Not enough tickets available."));
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

    private QueuePresenter mockQueuePresenter() {
        QueuePresenter queuePresenter = mock(QueuePresenter.class);
        when(queuePresenter.enterQueueGate(any())).thenReturn(QueueResult.directEntry("direct"));
        return queuePresenter;
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
        boolean found = root.getChildren().anyMatch(child -> hasButton(child, text));
        if (!found && root instanceof EventsView view && view.activeTicketDialog != null) {
            found = hasButton(view.activeTicketDialog, text);
        }
        return found;
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
        Button found = root.getChildren()
                .map(child -> findButton(child, text))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (found == null && root instanceof EventsView view && view.activeTicketDialog != null) {
            found = findButton(view.activeTicketDialog, text);
        }
        return found;
    }

    private boolean hasText(Component root, String text) {
        if (root instanceof HasText hasText && text.equals(hasText.getText())) {
            return true;
        }
        boolean found = root.getChildren().anyMatch(child -> hasText(child, text));
        if (!found && root instanceof EventsView view && view.activeTicketDialog != null) {
            found = hasText(view.activeTicketDialog, text);
        }
        return found;
    }

    @Test
    void GivenEventsView_WhenRendered_ThenResultsGridShowsEmptyStateMessage() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mockOrdersPresenter();

        EventsView view = new EventsView(presenter, ordersPresenter, mockQueuePresenter());

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

    private boolean containsText(Component root, String fragment) {
        if (root instanceof HasText hasText && hasText.getText() != null && hasText.getText().contains(fragment)) {
            return true;
        }
        boolean found = root.getChildren().anyMatch(child -> containsText(child, fragment));
        if (!found && root instanceof EventsView view && view.activeTicketDialog != null) {
            found = containsText(view.activeTicketDialog, fragment);
        }
        return found;
    }

    private long countComponents(Component root, Class<? extends Component> type) {
        long current = type.isInstance(root) ? 1 : 0;
        return current + root.getChildren()
                .mapToLong(child -> countComponents(child, type))
                .sum();
    }

    // ── Lottery UI tests ────────────────────────────────────────────

    @Test
    @DisplayName("Lottery event has LOTTERY sale method in the grid data")
    void GivenLotteryEvent_WhenSearching_ThenGridRowHasLotterySaleMethod() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        EventSummaryDTO lottery = lotteryEventSummary("Lottery Gig");
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(lottery)));
        EventsView view = new EventsView(presenter, mockOrdersPresenter(), mockQueuePresenter());

        clickButton(view, "Search events");

        List<EventSummaryDTO> rows = findGrid(view).getDataProvider().fetch(new com.vaadin.flow.data.provider.Query<>()).toList();
        assertEquals(1, rows.size());
        assertEquals(SaleMethod.LOTTERY, rows.get(0).saleMethod());
    }

    @Test
    @DisplayName("Lottery event dialog shows lottery explanation and hides normal purchase controls")
    void GivenLotteryEvent_WhenDialogOpened_ThenLotteryExplanationShownAndNoPurchaseControls() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        EventSummaryDTO lottery = lotteryEventSummary("Lottery Concert");
        EventMapDTO lotteryMap = lotteryEventMap(lottery.id(), false);
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(lottery)));
        when(presenter.loadEventMap(eq(lottery.id()))).thenReturn(MapResult.success("Event map loaded.", lotteryMap));
        when(presenter.getLotteryStatus(any())).thenReturn(Optional.empty());
        EventsView view = new EventsView(presenter, mockOrdersPresenter(), mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(lottery);
        clickButton(view, "Select tickets");

        assertTrue(containsText(view, "lottery"));
        assertFalse(containsText(view, "Choose your tickets"));
        assertFalse(hasButton(view, "Add selected seats to cart"));
    }

    @Test
    @DisplayName("Regular events show normal purchase controls, not lottery panel")
    void GivenRegularEvent_WhenDialogOpened_ThenNormalControlsShownAndNoLotteryPanel() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        EventSummaryDTO event = eventSummary("Normal Concert");
        EventMapDTO map = sampleEventMap(event.id());
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(event)));
        when(presenter.loadEventMap(eq(event.id()))).thenReturn(MapResult.success("Event map loaded.", map));
        EventsView view = new EventsView(presenter, mockOrdersPresenter(), mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "Select tickets");

        assertTrue(containsText(view, "Choose your tickets"));
        assertFalse(containsText(view, "allocated through a lottery"));
    }

    @Test
    @DisplayName("Guest sees members-only message for a lottery event")
    void GivenGuestSession_WhenLotteryDialogOpened_ThenMembersOnlyMessageIsShown() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        EventSummaryDTO lottery = lotteryEventSummary("Lottery Concert");
        EventMapDTO lotteryMap = lotteryEventMap(lottery.id(), true);
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(lottery)));
        when(presenter.loadEventMap(eq(lottery.id()))).thenReturn(MapResult.success("Event map loaded.", lotteryMap));
        SessionContext.setSessionToken("guest-token");
        EventsView view = new EventsView(presenter, mockOrdersPresenter(), mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(lottery);
        clickButton(view, "Select tickets");

        assertTrue(containsText(view, "Members only"));
        assertFalse(hasButton(view, "Enter lottery"));
    }

    @Test
    @DisplayName("Member sees registration form with zone and quantity for an open lottery window")
    void GivenMemberSession_WhenLotteryWindowOpen_ThenRegistrationFormIsVisible() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        EventSummaryDTO lottery = lotteryEventSummary("Lottery Concert");
        EventMapDTO lotteryMap = lotteryEventMap(lottery.id(), true);
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(lottery)));
        when(presenter.loadEventMap(eq(lottery.id()))).thenReturn(MapResult.success("Event map loaded.", lotteryMap));
        when(presenter.getLotteryStatus(any())).thenReturn(Optional.empty());
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        EventsView view = new EventsView(presenter, mockOrdersPresenter(), mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(lottery);
        clickButton(view, "Select tickets");

        assertTrue(hasButton(view, "Enter lottery"));
        assertNotNull(findZoneComboBox(view));
    }

    @Test
    @DisplayName("Member already registered sees confirmation, no registration form")
    void GivenMemberAlreadyRegistered_WhenLotteryDialogOpened_ThenRegistrationStatusIsShown() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        EventSummaryDTO lottery = lotteryEventSummary("Lottery Concert");
        EventMapDTO lotteryMap = lotteryEventMap(lottery.id(), true);
        Instant registeredAt = Instant.now().minus(30, ChronoUnit.MINUTES);
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(lottery)));
        when(presenter.loadEventMap(eq(lottery.id()))).thenReturn(MapResult.success("Event map loaded.", lotteryMap));
        when(presenter.getLotteryStatus(any())).thenReturn(
                Optional.of(LotteryRegistrationResponse.success(UUID.randomUUID(), registeredAt)));
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        EventsView view = new EventsView(presenter, mockOrdersPresenter(), mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(lottery);
        clickButton(view, "Select tickets");

        assertTrue(containsText(view, "You are registered for this lottery."));
        assertFalse(hasButton(view, "Enter lottery"));
    }

    @Test
    @DisplayName("Closed lottery window shows closed message, no registration form")
    void GivenClosedLotteryWindow_WhenMemberOpensDialog_ThenClosedMessageShownAndNoForm() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        EventSummaryDTO lottery = lotteryEventSummary("Lottery Concert");
        EventMapDTO lotteryMap = lotteryEventMap(lottery.id(), false);
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(lottery)));
        when(presenter.loadEventMap(eq(lottery.id()))).thenReturn(MapResult.success("Event map loaded.", lotteryMap));
        when(presenter.getLotteryStatus(any())).thenReturn(Optional.empty());
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        EventsView view = new EventsView(presenter, mockOrdersPresenter(), mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(lottery);
        clickButton(view, "Select tickets");

        assertTrue(containsText(view, "registration window is closed"));
        assertFalse(hasButton(view, "Enter lottery"));
    }

    @Test
    @DisplayName("Successful registration shows confirmation and disables the Enter lottery button")
    void GivenMemberRegisters_WhenSuccess_ThenConfirmationShownAndButtonDisabled() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        EventSummaryDTO lottery = lotteryEventSummary("Lottery Concert");
        EventMapDTO lotteryMap = lotteryEventMap(lottery.id(), true);
        Instant now = Instant.now();
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(lottery)));
        when(presenter.loadEventMap(eq(lottery.id()))).thenReturn(MapResult.success("Event map loaded.", lotteryMap));
        when(presenter.getLotteryStatus(any())).thenReturn(Optional.empty());
        when(presenter.registerForLottery(any(), any(), any()))
                .thenReturn(LotteryRegistrationResult.success("Lottery registration successful.", UUID.randomUUID(), now));
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        EventsView view = new EventsView(presenter, mockOrdersPresenter(), mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(lottery);
        clickButton(view, "Select tickets");

        ComboBox<EventMapDTO.ZoneInfo> zoneBox = findZoneComboBox(view);
        assertNotNull(zoneBox);
        zoneBox.setValue(lotteryMap.zones().get(0));
        clickButton(view, "Enter lottery");

        assertTrue(containsText(view, "You are registered for this lottery."));
        Button enterBtn = findButton(view, "Registered");
        assertNotNull(enterBtn);
        assertFalse(enterBtn.isEnabled());
    }

    @Test
    @DisplayName("Failed registration shows specific reason returned by the service")
    void GivenRegistrationFails_WhenEnterLotteryClicked_ThenSpecificReasonIsShown() {
        EventsPresenter presenter = mock(EventsPresenter.class);
        EventSummaryDTO lottery = lotteryEventSummary("Lottery Concert");
        EventMapDTO lotteryMap = lotteryEventMap(lottery.id(), true);
        whenSearch(presenter).thenReturn(SearchResult.success("Found 1 event(s).", List.of(lottery)));
        when(presenter.loadEventMap(eq(lottery.id()))).thenReturn(MapResult.success("Event map loaded.", lotteryMap));
        when(presenter.getLotteryStatus(any())).thenReturn(Optional.empty());
        when(presenter.registerForLottery(any(), any(), any()))
                .thenReturn(LotteryRegistrationResult.failure("Member is already registered for this lottery."));
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        EventsView view = new EventsView(presenter, mockOrdersPresenter(), mockQueuePresenter());

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(lottery);
        clickButton(view, "Select tickets");

        ComboBox<EventMapDTO.ZoneInfo> zoneBox = findZoneComboBox(view);
        assertNotNull(zoneBox);
        zoneBox.setValue(lotteryMap.zones().get(0));
        clickButton(view, "Enter lottery");

        assertTrue(containsText(view, "Member is already registered for this lottery."));
        assertTrue(hasButton(view, "Enter lottery"));
    }

    @SuppressWarnings("unchecked")
    private ComboBox<EventMapDTO.ZoneInfo> findZoneComboBox(EventsView view) {
        List<Component> all = new java.util.ArrayList<>(componentsOf(view));
        if (view.activeTicketDialog != null) {
            all.addAll(componentsOf(view.activeTicketDialog));
        }
        return all.stream()
                .filter(ComboBox.class::isInstance)
                .map(c -> (ComboBox<?>) c)
                .filter(combo -> "Select zone".equals(combo.getLabel()))
                .map(c -> (ComboBox<EventMapDTO.ZoneInfo>) c)
                .findFirst()
                .orElse(null);
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
        Instant open = windowOpen
                ? Instant.now().minus(1, ChronoUnit.HOURS)
                : Instant.now().minus(2, ChronoUnit.HOURS);
        Instant close = windowOpen
                ? Instant.now().plus(1, ChronoUnit.HOURS)
                : Instant.now().minus(1, ChronoUnit.HOURS);
        EventMapDTO.LotteryInfo lotteryInfo = new EventMapDTO.LotteryInfo(open, close, windowOpen);
        return new EventMapDTO(
                eventId,
                "Lottery Concert",
                "Acme",
                EventStatus.PUBLISHED,
                Map.of("Floor", zoneId),
                List.of(new EventMapDTO.ZoneInfo(
                        zoneId, "Floor", ZoneType.GENERAL_ADMISSION,
                        new BigDecimal("50.00"), 200, 150, 50, List.of())),
                null, "", List.of(), List.of(),
                lotteryInfo);
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
        EventMapDTO.SeatInfo a1 = new EventMapDTO.SeatInfo(UUID.randomUUID(), "A", "1", true);
        EventMapDTO.SeatInfo a2 = new EventMapDTO.SeatInfo(UUID.randomUUID(), "A", "2", false);
        EventMapDTO.LayoutInfo layout = new EventMapDTO.LayoutInfo(1, 3, List.of(
                new EventMapDTO.CellInfo(0, 0, LayoutCellType.GENERAL_ADMISSION, "Floor", floorId, null),
                new EventMapDTO.CellInfo(0, 1, LayoutCellType.SEAT, null, balconyId, a1.id()),
                new EventMapDTO.CellInfo(0, 2, LayoutCellType.SEAT, null, balconyId, a2.id())));
        return new EventMapDTO(
                eventId,
                "Spring Concert",
                "Acme",
                EventStatus.PUBLISHED,
                Map.of("Floor", floorId, "Balcony", balconyId),
                List.of(
                        new EventMapDTO.ZoneInfo(
                                floorId, "Floor", ZoneType.GENERAL_ADMISSION,
                                new BigDecimal("45.00"), 100, 10, 90, List.of()),
                        new EventMapDTO.ZoneInfo(
                                balconyId, "Balcony", ZoneType.ASSIGNED_SEATING,
                                new BigDecimal("95.00"), null, null, null, List.of(a1, a2))),
                layout);
    }

    // Assigned zone with seats supplied out of order (across rows and seat numbers)
    // so a rendering test can assert the seat map sorts them: rows by label, seats
    // numerically. A-10 is taken to exercise free/taken status alongside ordering.
    private static EventMapDTO seatMapEventMap(UUID eventId) {
        UUID balconyId = UUID.randomUUID();
        EventMapDTO.SeatInfo a10 = new EventMapDTO.SeatInfo(UUID.randomUUID(), "A", "10", false);
        EventMapDTO.SeatInfo b1 = new EventMapDTO.SeatInfo(UUID.randomUUID(), "B", "1", true);
        EventMapDTO.SeatInfo a1 = new EventMapDTO.SeatInfo(UUID.randomUUID(), "A", "1", true);
        EventMapDTO.SeatInfo a2 = new EventMapDTO.SeatInfo(UUID.randomUUID(), "A", "2", true);
        EventMapDTO.LayoutInfo layout = new EventMapDTO.LayoutInfo(1, 4, List.of(
                new EventMapDTO.CellInfo(0, 0, LayoutCellType.SEAT, null, balconyId, a10.id()),
                new EventMapDTO.CellInfo(0, 1, LayoutCellType.SEAT, null, balconyId, b1.id()),
                new EventMapDTO.CellInfo(0, 2, LayoutCellType.SEAT, null, balconyId, a1.id()),
                new EventMapDTO.CellInfo(0, 3, LayoutCellType.SEAT, null, balconyId, a2.id())));
        return new EventMapDTO(
                eventId,
                "Spring Concert",
                "Acme",
                EventStatus.PUBLISHED,
                Map.of("Balcony", balconyId),
                List.of(
                        new EventMapDTO.ZoneInfo(
                                balconyId, "Balcony", ZoneType.ASSIGNED_SEATING,
                                new BigDecimal("95.00"), null, null, null,
                                List.of(a10, b1, a1, a2))),
                layout);
    }
}
