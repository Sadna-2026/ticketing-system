package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.LayoutCellType;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.MapResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderMutationResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderResult;
import com.ticketing.presentation.vaadin.presenters.QueuePresenter;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;

@DisplayName("EventsView drag seat selection")
@ExtendWith(VaadinSessionExtension.class)
class EventsViewSeatDragSelectionTest {

    @Test
    void GivenDraggedSeatRange_WhenSeatsAreAdded_ThenContiguousBlockUsesMultiSeatReservation() throws Exception {
        EventsPresenter eventsPresenter = mock(EventsPresenter.class);
        OrdersPresenter ordersPresenter = mock(OrdersPresenter.class);
        QueuePresenter queuePresenter = mock(QueuePresenter.class);
        when(eventsPresenter.searchCompanies("")).thenReturn(List.of());
        when(ordersPresenter.currentSessionLabel()).thenReturn("Current session: Guest");
        when(ordersPresenter.currentSessionState())
                .thenReturn(new com.ticketing.presentation.vaadin.util.SessionContext.UiState(true, true, false, false, null, null));
        when(ordersPresenter.loadCurrentOrder()).thenReturn(OrderResult.success("No active order found.", null, null));
        when(ordersPresenter.addAssignedSeats(any(), any(), any()))
                .thenReturn(OrderMutationResult.success("4 assigned seats added.", null, null));
        EventsView view = new EventsView(eventsPresenter, ordersPresenter, queuePresenter);

        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID b1 = UUID.randomUUID();
        UUID b2 = UUID.randomUUID();
        EventMapDTO map = assignedMap(eventId, zoneId, a1, a2, b1, b2);
        when(eventsPresenter.loadEventMap(eventId)).thenReturn(MapResult.success("Event map loaded.", map));

        setField(view, "directlyAdmittedEventId", eventId);
        invokeRenderInteractiveMap(view, map);

        view.selectSeatRange(a1, b2);
        assertEquals(Set.of(a1, a2, b1, b2), selectedSeatIds(view));

        view.addSelectedSeats();

        ArgumentCaptor<List<UUID>> seatsCaptor = ArgumentCaptor.forClass(List.class);
        verify(ordersPresenter).addAssignedSeats(eq(eventId), eq(zoneId), seatsCaptor.capture());
        assertEquals(Set.of(a1, a2, b1, b2), Set.copyOf(seatsCaptor.getValue()));
    }

    private static EventMapDTO assignedMap(UUID eventId, UUID zoneId, UUID a1, UUID a2, UUID b1, UUID b2) {
        List<EventMapDTO.SeatInfo> seats = List.of(
                new EventMapDTO.SeatInfo(a1, "A", "1", true),
                new EventMapDTO.SeatInfo(a2, "A", "2", true),
                new EventMapDTO.SeatInfo(b1, "B", "1", true),
                new EventMapDTO.SeatInfo(b2, "B", "2", true));
        List<EventMapDTO.CellInfo> cells = List.of(
                new EventMapDTO.CellInfo(0, 0, LayoutCellType.SEAT, null, zoneId, a1),
                new EventMapDTO.CellInfo(0, 1, LayoutCellType.SEAT, null, zoneId, a2),
                new EventMapDTO.CellInfo(1, 0, LayoutCellType.SEAT, null, zoneId, b1),
                new EventMapDTO.CellInfo(1, 1, LayoutCellType.SEAT, null, zoneId, b2));
        EventMapDTO.ZoneInfo zone = new EventMapDTO.ZoneInfo(
                zoneId, "Front", ZoneType.ASSIGNED_SEATING, BigDecimal.TEN, null, null, null, seats);
        return new EventMapDTO(
                eventId,
                "Drag QA",
                "Demo",
                EventStatus.PUBLISHED,
                Map.of(),
                List.of(zone),
                new EventMapDTO.LayoutInfo(2, 2, cells));
    }

    private static void invokeRenderInteractiveMap(EventsView view, EventMapDTO map) throws Exception {
        Method method = EventsView.class.getDeclaredMethod("renderInteractiveMap", EventMapDTO.class);
        method.setAccessible(true);
        method.invoke(view, map);
    }

    private static void setField(EventsView view, String name, Object value) throws Exception {
        Field field = EventsView.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(view, value);
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> selectedSeatIds(EventsView view) throws Exception {
        Field field = EventsView.class.getDeclaredField("selectedSeatIds");
        field.setAccessible(true);
        return Set.copyOf((Set<UUID>) field.get(view));
    }
}
