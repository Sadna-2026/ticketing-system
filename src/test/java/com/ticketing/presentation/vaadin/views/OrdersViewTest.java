package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.OrderItemDto;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.CheckoutResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.HistoryResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.InventoryResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderMutationResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderResult;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.Query;

@DisplayName("OrdersView")
class OrdersViewTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void GivenGuestSession_WhenRendered_ThenOrderCheckoutControlsAreVisibleAndHistoryIsHidden() {
        OrdersPresenter presenter = mockPresenter();

        OrdersView view = new OrdersView(presenter);

        assertTrue(hasVisibleButton(view, "Load event inventory"));
        assertTrue(hasVisibleButton(view, "Checkout"));
        assertFalse(hasVisibleButton(view, "Load purchase history"));
        assertTrue(hasText(view, "Log in as a member to view purchase history."));
        assertNotNull(findTextField(view, "Event ID"));
        assertNotNull(findTextField(view, "Coupon code"));
        assertNotNull(findIntegerField(view, "New GA quantity"));
        assertEquals(2, findGrids(view).size());
    }

    @Test
    void GivenGuestSession_WhenRendered_ThenActiveOrderSummaryIsDisplayedIfItExists() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of());
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        OrdersView view = new OrdersView(presenter);

        assertEquals(eventId.toString(), findTextField(view, "Event ID").getValue());
        assertTrue(hasText(view, "Active order loaded."));
        assertTrue(hasText(view, "Order " + orderId + " | status ACTIVE | tickets 0 | total 0"));
    }

    @Test
    void GivenActiveOrderAndTicketSelection_WhenAddingGaAndAssignedTickets_ThenOrderSummaryRefreshes() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID gaZoneId = UUID.randomUUID();
        UUID seatZoneId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID gaItemId = UUID.randomUUID();
        UUID seatItemId = UUID.randomUUID();
        EventMapDTO eventMap = eventMap(eventId, gaZoneId, seatZoneId, seatId);
        ActiveOrderDto emptyOrder = activeOrder(orderId, eventId, List.of());
        ActiveOrderDto gaOrder = activeOrder(orderId, eventId, List.of(gaItem(gaItemId, gaZoneId, 2)));
        ActiveOrderDto fullOrder = activeOrder(orderId, eventId, List.of(
                gaItem(gaItemId, gaZoneId, 2),
                seatItem(seatItemId, seatZoneId, seatId)));
        when(presenter.loadEventInventory(eventId)).thenReturn(InventoryResult.success("Event inventory loaded.", eventMap));
        when(presenter.addGATickets(eventId, gaZoneId, 1))
                .thenReturn(OrderMutationResult.success("GA tickets added.", gaItemId, gaOrder));
        when(presenter.addAssignedSeat(eventId, seatZoneId, seatId))
                .thenReturn(OrderMutationResult.success("Assigned seat added.", seatItemId, fullOrder));
        OrdersView view = new OrdersView(presenter);
        findTextField(view, "Event ID").setValue(eventId.toString());

        clickButton(view, "Load event inventory");
        assertTrue(hasText(view, "Event inventory loaded."));
        clickButton(view, "Add GA tickets");
        assertTrue(hasText(view, "GA tickets added."));
        clickButton(view, "Add A-1");

        assertTrue(hasText(view, "Assigned seat added."));
        assertTrue(hasText(view, "Order " + orderId + " | status ACTIVE | tickets 3 | total 250.00"));
        verify(presenter).addGATickets(eventId, gaZoneId, 1);
        verify(presenter).addAssignedSeat(eventId, seatZoneId, seatId);
    }

    @Test
    void GivenOrderId_WhenViewingActiveOrder_ThenItemsAndTotalAreDisplayed() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OrderItemDto item = gaItem(UUID.randomUUID(), UUID.randomUUID(), 3);
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(item));
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        OrdersView view = new OrdersView(presenter);

        Grid<OrderItemDto> grid = findOrderItemsGrid(view);
        List<OrderItemDto> rows = grid.getDataProvider().fetch(new Query<>()).toList();
        assertEquals(List.of(item), rows);
        assertTrue(hasText(view, "Active order loaded."));
        assertTrue(hasText(view, "Order " + orderId + " | status ACTIVE | tickets 3 | total 150.00"));
    }

    @Test
    void GivenOrderWithCoupon_WhenCheckoutClicked_ThenPurchaseIdIsDisplayed() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(gaItem(UUID.randomUUID(), UUID.randomUUID(), 1)));
        when(presenter.checkout("SAVE20")).thenReturn(CheckoutResult.success("Checkout complete.", purchaseId));
        OrdersView view = new OrdersView(presenter);
        findTextField(view, "Event ID").setValue(eventId.toString());
        findTextField(view, "Coupon code").setValue("SAVE20");

        clickButton(view, "Checkout");

        assertTrue(hasText(view, "Checkout complete."));
        assertTrue(hasText(view, "Checkout complete. Purchase ID: " + purchaseId));
        assertTrue(findOrderItemsGrid(view).getDataProvider().fetch(new Query<>()).toList().isEmpty());
        assertTrue(hasText(view, "Create or load an order to see active order details."));
        verify(presenter).checkout("SAVE20");
    }

    @Test
    void GivenMemberSession_WhenLoadingPurchaseHistory_ThenPurchaseRecordsAreDisplayed() {
        OrdersPresenter presenter = mockPresenter();
        PurchaseRecordDTO purchase = purchase();
        when(presenter.currentSessionLabel()).thenReturn("Current session: Member (alice)");
        when(presenter.currentSessionState()).thenReturn(member());
        when(presenter.loadPurchaseHistory()).thenReturn(HistoryResult.success("Loaded 1 purchase(s).", List.of(purchase)));
        OrdersView view = new OrdersView(presenter);

        clickButton(view, "Load purchase history");

        Grid<PurchaseRecordDTO> grid = findHistoryGrid(view);
        List<PurchaseRecordDTO> rows = grid.getDataProvider().fetch(new Query<>()).toList();
        assertEquals(List.of(purchase), rows);
        assertTrue(hasText(view, "Loaded 1 purchase(s)."));
    }

    @Test
    void GivenPurchaseHistoryFails_WhenLoadingPurchaseHistory_ThenFailureMessageIsShownInline() {
        OrdersPresenter presenter = mockPresenter();
        when(presenter.loadPurchaseHistory())
                .thenReturn(HistoryResult.failure("Could not load purchase history. Please try again."));
        OrdersView view = new OrdersView(presenter);

        clickButton(view, "Load purchase history");

        assertTrue(hasText(view, "Could not load purchase history. Please try again."));
        assertTrue(findHistoryGrid(view).getDataProvider().fetch(new Query<>()).toList().isEmpty());
        verify(presenter).loadPurchaseHistory();
    }

    @Test
    void GivenInvalidEventId_WhenCreatingOrder_ThenInlineValidationMessageIsShown() {
        OrdersPresenter presenter = mockPresenter();
        OrdersView view = new OrdersView(presenter);
        findTextField(view, "Event ID").setValue("not-a-uuid");

        clickButton(view, "Load event inventory");

        assertTrue(hasText(view, "Enter a valid event ID."));
    }

    @Test
    void GivenApplicationServiceThrows_WhenOrderActionRuns_ThenUserFacingErrorIsShown() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.failure("Session already has an active order"));
        OrdersView view = new OrdersView(presenter);

        assertTrue(hasText(view, "Session already has an active order"));
    }

    @Test
    void GivenActiveOrderAndTicketSelectionFails_WhenAddingGaTickets_ThenFailureMessageIsShownInline() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID gaZoneId = UUID.randomUUID();
        UUID seatZoneId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        EventMapDTO eventMap = eventMap(eventId, gaZoneId, seatZoneId, seatId);
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of());
        when(presenter.loadEventInventory(eventId)).thenReturn(InventoryResult.success("Event inventory loaded.", eventMap));
        when(presenter.addGATickets(eventId, gaZoneId, 1))
                .thenReturn(OrderMutationResult.failure("Only 0 ticket(s) remain available in this zone"));
        OrdersView view = new OrdersView(presenter);
        findTextField(view, "Event ID").setValue(eventId.toString());

        clickButton(view, "Load event inventory");
        clickButton(view, "Add GA tickets");

        assertTrue(hasText(view, "Only 0 ticket(s) remain available in this zone"));
        verify(presenter).addGATickets(eventId, gaZoneId, 1);
    }

    @Test
    void GivenPolicyFailure_WhenCheckoutClicked_ThenPolicyMessageIsShownInline() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String policyMessage = "Purchase policy violation: AGE_RESTRICTED - Buyer does not meet age policy";
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(gaItem(UUID.randomUUID(), UUID.randomUUID(), 1)));
        when(presenter.checkout("")).thenReturn(CheckoutResult.failure(policyMessage));
        OrdersView view = new OrdersView(presenter);
        findTextField(view, "Event ID").setValue(eventId.toString());

        clickButton(view, "Checkout");

        assertTrue(hasText(view, policyMessage));
        verify(presenter).checkout("");
    }

    @Test
    void GivenSelectedOrderItem_WhenRemovingAndUpdatingFail_ThenFailureMessagesAreShownInline() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        OrderItemDto item = gaItem(itemId, zoneId, 2);
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(item));
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        when(presenter.updateGAQuantity(zoneId, 4))
                .thenReturn(OrderMutationResult.failure("GA quantity exceeds remaining availability."));
        when(presenter.removeItem(itemId))
                .thenReturn(OrderMutationResult.failure("Order item could not be removed."));
        OrdersView view = new OrdersView(presenter);

        findOrderItemsGrid(view).asSingleSelect().setValue(item);
        findIntegerField(view, "New GA quantity").setValue(4);
        clickButton(view, "Update selected GA quantity");
        assertTrue(hasText(view, "GA quantity exceeds remaining availability."));

        findOrderItemsGrid(view).asSingleSelect().setValue(item);
        clickButton(view, "Remove selected item");

        assertTrue(hasText(view, "Order item could not be removed."));
        verify(presenter).updateGAQuantity(zoneId, 4);
        verify(presenter).removeItem(itemId);
    }

    @Test
    void GivenSelectedOrderItem_WhenRemovingAndUpdating_ThenPresenterActionsAreCalled() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        OrderItemDto item = gaItem(itemId, zoneId, 2);
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(item));
        ActiveOrderDto updatedOrder = activeOrder(orderId, eventId, List.of(gaItem(itemId, zoneId, 4)));
        ActiveOrderDto emptyOrder = activeOrder(orderId, eventId, List.of());
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        when(presenter.updateGAQuantity(zoneId, 4))
                .thenReturn(OrderMutationResult.success("GA quantity updated.", null, updatedOrder));
        when(presenter.removeItem(itemId))
                .thenReturn(OrderMutationResult.success("Order item removed.", itemId, emptyOrder));
        OrdersView view = new OrdersView(presenter);

        findOrderItemsGrid(view).asSingleSelect().setValue(item);
        findIntegerField(view, "New GA quantity").setValue(4);
        clickButton(view, "Update selected GA quantity");
        assertTrue(hasText(view, "GA quantity updated."));
        findOrderItemsGrid(view).asSingleSelect().setValue(updatedOrder.getItems().get(0));
        clickButton(view, "Remove selected item");

        assertTrue(hasText(view, "Order item removed."));
        assertTrue(hasText(view, "Order " + orderId + " | status ACTIVE | tickets 0 | total 0"));
        verify(presenter).updateGAQuantity(zoneId, 4);
        verify(presenter).removeItem(itemId);
    }

    private OrdersPresenter mockPresenter() {
        OrdersPresenter presenter = mock(OrdersPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Guest");
        when(presenter.currentSessionState()).thenReturn(guest());
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.failure("No active order"));
        return presenter;
    }

    private boolean hasVisibleButton(Component root, String text) {
        Button button = findButton(root, text);
        return button != null && isEffectivelyVisible(button);
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

    private TextField findTextField(Component root, String label) {
        if (root instanceof TextField textField && label.equals(textField.getLabel())) {
            return textField;
        }
        return root.getChildren()
                .map(child -> findTextField(child, label))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private IntegerField findIntegerField(Component root, String label) {
        if (root instanceof IntegerField integerField && label.equals(integerField.getLabel())) {
            return integerField;
        }
        return root.getChildren()
                .map(child -> findIntegerField(child, label))
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

    private boolean isEffectivelyVisible(Component component) {
        if (!component.isVisible()) {
            return false;
        }
        return component.getParent()
                .map(this::isEffectivelyVisible)
                .orElse(true);
    }

    private static SessionContext.UiState guest() {
        return new SessionContext.UiState(true, true, false, false, null, "Guest");
    }

    private static SessionContext.UiState member() {
        return new SessionContext.UiState(true, false, true, false, "alice", "Member");
    }

    @SuppressWarnings("unchecked")
    private Grid<OrderItemDto> findOrderItemsGrid(Component root) {
        return (Grid<OrderItemDto>) findGrids(root).get(0);
    }

    @SuppressWarnings("unchecked")
    private Grid<PurchaseRecordDTO> findHistoryGrid(Component root) {
        return (Grid<PurchaseRecordDTO>) findGrids(root).get(1);
    }

    private List<Grid<?>> findGrids(Component root) {
        List<Grid<?>> grids = new ArrayList<>();
        collectGrids(root, grids);
        return grids;
    }

    private void collectGrids(Component root, List<Grid<?>> grids) {
        if (root instanceof Grid<?> grid) {
            grids.add(grid);
        }
        root.getChildren().forEach(child -> collectGrids(child, grids));
    }

    private static ActiveOrderDto activeOrder(UUID orderId, UUID eventId, List<OrderItemDto> items) {
        BigDecimal total = items.stream()
                .map(OrderItemDto::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ActiveOrderDto(
                orderId,
                UUID.randomUUID(),
                null,
                eventId,
                Instant.parse("2026-05-26T12:00:00Z"),
                "ACTIVE",
                items,
                total);
    }

    private static OrderItemDto gaItem(UUID itemId, UUID zoneId, int quantity) {
        return new OrderItemDto(
                itemId,
                zoneId,
                null,
                quantity,
                new BigDecimal("50.00"),
                new BigDecimal("50.00").multiply(BigDecimal.valueOf(quantity)),
                false);
    }

    private static OrderItemDto seatItem(UUID itemId, UUID zoneId, UUID seatId) {
        return new OrderItemDto(
                itemId,
                zoneId,
                seatId,
                1,
                new BigDecimal("150.00"),
                new BigDecimal("150.00"),
                true);
    }

    private static EventMapDTO eventMap(UUID eventId, UUID gaZoneId, UUID seatZoneId, UUID seatId) {
        return new EventMapDTO(
                eventId,
                "Spring Concert",
                "Acme",
                EventStatus.PUBLISHED,
                Map.of("Floor", gaZoneId, "Balcony", seatZoneId),
                List.of(
                        new EventMapDTO.ZoneInfo(
                                gaZoneId,
                                "Floor",
                                ZoneType.GENERAL_ADMISSION,
                                new BigDecimal("50.00"),
                                100,
                                80,
                                20,
                                List.of()),
                        new EventMapDTO.ZoneInfo(
                                seatZoneId,
                                "Balcony",
                                ZoneType.ASSIGNED_SEATING,
                                new BigDecimal("150.00"),
                                null,
                                null,
                                null,
                                List.of(new EventMapDTO.SeatInfo(seatId, "A", "1", true)))
                ));
    }

    private static PurchaseRecordDTO purchase() {
        return new PurchaseRecordDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Spring Concert",
                "Acme",
                UUID.randomUUID(),
                "TXN-1",
                new BigDecimal("80.00"),
                Instant.parse("2026-05-26T12:00:00Z"));
    }
}
