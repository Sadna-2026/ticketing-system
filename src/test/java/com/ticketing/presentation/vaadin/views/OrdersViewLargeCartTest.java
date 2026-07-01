package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.application.dto.OrderItemDto;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.grid.Grid;

@DisplayName("OrdersView large cart rendering")
@ExtendWith(VaadinSessionExtension.class)
class OrdersViewLargeCartTest {

    @Test
    void GivenLargeAssignedSeatOrder_WhenViewIsCreated_ThenOrderItemsGridUsesVirtualScrolling() throws Exception {
        OrdersPresenter presenter = mock(OrdersPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Member (owner)");
        when(presenter.currentSessionState())
                .thenReturn(new SessionContext.UiState(true, false, true, false, "owner", "MEMBER"));
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("No active order found.", null, null));

        OrdersView view = new OrdersView(presenter);

        Grid<OrderItemDto> grid = orderItemsGrid(view);
        assertFalse(grid.isAllRowsVisible());
        assertEquals(100, grid.getPageSize());
    }

    @SuppressWarnings("unchecked")
    private static Grid<OrderItemDto> orderItemsGrid(OrdersView view) throws Exception {
        Field field = OrdersView.class.getDeclaredField("orderItemsGrid");
        field.setAccessible(true);
        return (Grid<OrderItemDto>) field.get(view);
    }
}
