package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.MapResult;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.SearchResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderMutationResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderResult;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.Query;

@DisplayName("EventsView")
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
        assertEquals(3, countComponents(view, TextField.class));
        assertEquals(1, countComponents(view, ComboBox.class));
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
        assertTrue(hasButton(view, "Add A-1"));
        assertTrue(hasButton(view, "Add A-2"));
        assertFalse(isButtonEnabled(view, "Add A-2"));
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
        when(ordersPresenter.addGATickets(event.id(), gaZoneId, 1))
                .thenReturn(OrderMutationResult.success("GA tickets added.", UUID.randomUUID(), null));
        when(ordersPresenter.addAssignedSeat(event.id(), seatZoneId, seatId))
                .thenReturn(OrderMutationResult.success("Assigned seat added.", UUID.randomUUID(), null));
        EventsView view = new EventsView(presenter, ordersPresenter);

        clickButton(view, "Search events");
        findGrid(view).asSingleSelect().setValue(event);
        clickButton(view, "View selected map");
        clickButton(view, "Add GA tickets");
        assertTrue(hasText(view, "GA tickets added."));
        clickButton(view, "Add A-1");
        assertTrue(hasText(view, "Assigned seat added."));
        verify(ordersPresenter).addGATickets(event.id(), gaZoneId, 1);
        verify(ordersPresenter).addAssignedSeat(event.id(), seatZoneId, seatId);
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

    private boolean isButtonEnabled(Component root, String text) {
        Button button = findButton(root, text);
        return button != null && button.isEnabled();
    }

    private boolean hasText(Component root, String text) {
        if (root instanceof HasText hasText && text.equals(hasText.getText())) {
            return true;
        }
        return root.getChildren().anyMatch(child -> hasText(child, text));
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
                EventStatus.PUBLISHED
        );
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
}
