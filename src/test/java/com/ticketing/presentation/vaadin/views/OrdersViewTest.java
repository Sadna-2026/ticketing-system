package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.dto.OrderItemDto;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.CheckoutQuoteResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.CheckoutResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.HistoryResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderComplianceResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderLabels;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderMutationResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.Query;

@DisplayName("OrdersView")
@ExtendWith(VaadinSessionExtension.class)
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

        assertTrue(hasVisibleButton(view, "Checkout"));
        assertFalse(hasVisibleButton(view, "Load purchase history"));
        assertTrue(hasText(view, "Log in as a member to view purchase history."));
        assertTrue(hasText(view, "Review your active order, update quantities, checkout, and view purchase history. Browse events and add tickets on the Events page."));
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

        assertTrue(hasText(view, "Active order loaded."));
        assertTrue(hasText(view, "Order " + orderId + " | event " + eventId + " | status ACTIVE | tickets 0 | total 0"));
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
        assertTrue(hasText(view, "Order " + orderId + " | event " + eventId + " | status ACTIVE | tickets 3 | total 150.00"));
    }

    @Test
    void GivenOrderWithSeat_WhenRendered_ThenCartShowsZoneAndSeatLabelColumnsNotUuids() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        OrderItemDto item = seatItem(UUID.randomUUID(), zoneId, seatId);
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(item));
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        when(presenter.labelsFor(order)).thenReturn(new OrderLabels(Map.of(zoneId, "Balcony"), Map.of(seatId, "A-1")));

        OrdersView view = new OrdersView(presenter);

        List<String> headers = columnHeaders(findOrderItemsGrid(view));
        assertTrue(headers.contains("Zone"), headers.toString());
        assertTrue(headers.contains("Seat"), headers.toString());
        assertFalse(headers.contains("Zone ID"));
        assertFalse(headers.contains("Seat ID"));
        verify(presenter).labelsFor(order);
    }

    @Test
    void GivenOrder_WhenRendered_ThenCartHidesRawItemIdButKeepsItOnTheBoundRow() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        OrderItemDto item = gaItem(itemId, UUID.randomUUID(), 2);
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(item));
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        OrdersView view = new OrdersView(presenter);

        Grid<OrderItemDto> grid = findOrderItemsGrid(view);
        List<String> headers = columnHeaders(grid);
        assertFalse(headers.contains("Item ID"), headers.toString());
        assertTrue(headers.contains("Zone"), headers.toString());
        assertTrue(headers.contains("Quantity"), headers.toString());
        // The id is dropped from the columns but stays on the bound row so remove/update act on the right item.
        List<OrderItemDto> rows = grid.getDataProvider().fetch(new Query<>()).toList();
        assertEquals(itemId, rows.get(0).getId());
    }

    @Test
    void GivenPurchaseHistory_WhenRendered_ThenGridShowsEventAndDateNotRawPurchaseId() {
        OrdersPresenter presenter = mockPresenter();
        PurchaseRecordDTO purchase = purchase();
        when(presenter.currentSessionLabel()).thenReturn("Current session: Member (alice)");
        when(presenter.currentSessionState()).thenReturn(member());
        when(presenter.loadPurchaseHistory()).thenReturn(HistoryResult.success("Loaded 1 purchase(s).", List.of(purchase)));
        OrdersView view = new OrdersView(presenter);

        clickButton(view, "Load purchase history");

        Grid<PurchaseRecordDTO> grid = findHistoryGrid(view);
        List<String> headers = columnHeaders(grid);
        assertFalse(headers.contains("Purchase ID"), headers.toString());
        assertTrue(headers.contains("Event"), headers.toString());
        assertTrue(headers.contains("Purchased at"), headers.toString());
        // The purchase id is still carried by the bound row even though it is no longer a column.
        List<PurchaseRecordDTO> rows = grid.getDataProvider().fetch(new Query<>()).toList();
        assertEquals(purchase.purchaseId(), rows.get(0).purchaseId());
    }

    @Test
    void GivenEmptiedOrderPinnedToEvent_WhenClearCartClicked_ThenOrderIsClearedViaPresenter() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        // The dead-end: an order still exists (pinned to its event) but has no items.
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of());
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        when(presenter.cancelOrder()).thenReturn(OrderMutationResult.success("Cart cleared.", null, null));
        OrdersView view = new OrdersView(presenter);

        assertTrue(hasVisibleButton(view, "Clear cart"));
        clickButton(view, "Clear cart");

        assertTrue(hasText(view, "Cart cleared."));
        assertTrue(hasText(view, "No active order. Add tickets from the Events page."));
        verify(presenter).cancelOrder();
    }

    @Test
    void GivenNoActiveOrder_WhenRendered_ThenCheckoutButtonIsDisabled() {
        OrdersPresenter presenter = mockPresenter();
        OrdersView view = new OrdersView(presenter);

        assertFalse(findButton(view, "Checkout").isEnabled());
        assertTrue(hasText(view, "Checkout is available once the active order has tickets."));
    }

    @Test
    void GivenOrderViolatesPolicy_WhenRendered_ThenCheckoutDisabledAndReasonShown() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(gaItem(UUID.randomUUID(), UUID.randomUUID(), 1)));
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        when(presenter.checkOrderCompliance())
                .thenReturn(OrderComplianceResult.nonCompliant("You must purchase at least 2 tickets"));
        OrdersView view = new OrdersView(presenter);

        assertFalse(findButton(view, "Checkout").isEnabled());
        assertTrue(hasText(view, "Purchase policy not met: You must purchase at least 2 tickets"));
        assertTrue(hasText(view, "Resolve the issue above before checkout."));
    }

    @Test
    void GivenOrderWithDiscountQuote_WhenCouponEntered_ThenSubtotalAndAmountDueAreShown() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(gaItem(UUID.randomUUID(), UUID.randomUUID(), 2)));
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        when(presenter.quoteCheckout(""))
                .thenReturn(CheckoutQuoteResult.success(new BigDecimal("100.00"), new BigDecimal("100.00")));
        when(presenter.quoteCheckout("SAVE20"))
                .thenReturn(CheckoutQuoteResult.success(new BigDecimal("100.00"), new BigDecimal("80.00")));
        OrdersView view = new OrdersView(presenter);

        findTextField(view, "Coupon code").setValue("SAVE20");

        assertTrue(containsText(view, "Subtotal 100.00 | Amount due: 80.00"));
        verify(presenter).quoteCheckout("SAVE20");
    }

    @Test
    void GivenOrderWithoutDiscount_WhenRendered_ThenAmountDueShowsSingleTotalLine() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(gaItem(UUID.randomUUID(), UUID.randomUUID(), 1)));
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        when(presenter.quoteCheckout(""))
                .thenReturn(CheckoutQuoteResult.success(new BigDecimal("50.00"), new BigDecimal("50.00")));
        OrdersView view = new OrdersView(presenter);

        assertTrue(containsText(view, "Amount due: 50.00"));
        assertFalse(containsText(view, "Subtotal 50.00 | Amount due:"));
    }

    @Test
    void GivenQuoteFailure_WhenCheckoutStateRefreshed_ThenQuoteMessageIsShown() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(gaItem(UUID.randomUUID(), UUID.randomUUID(), 1)));
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        when(presenter.quoteCheckout(""))
                .thenReturn(CheckoutQuoteResult.failure("Coupon expired"));
        OrdersView view = new OrdersView(presenter);

        assertTrue(hasText(view, "Coupon expired"));
    }

    @Test
    void GivenBlankQuoteFailure_WhenCheckoutStateRefreshed_ThenDefaultHintIsShown() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(gaItem(UUID.randomUUID(), UUID.randomUUID(), 1)));
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        when(presenter.quoteCheckout(""))
                .thenReturn(CheckoutQuoteResult.failure(""));
        OrdersView view = new OrdersView(presenter);

        assertTrue(hasText(view, "Enter an optional coupon code, then checkout."));
    }

    @Test
    void GivenCheckoutSuccessWithoutChargedAmount_WhenCheckoutClicked_ThenPurchaseIdStillShown() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(gaItem(UUID.randomUUID(), UUID.randomUUID(), 1)));
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        when(presenter.quoteCheckout("")).thenReturn(CheckoutQuoteResult.success(new BigDecimal("50.00"), new BigDecimal("50.00")));
        when(presenter.checkout("")).thenReturn(CheckoutResult.success("Checkout complete.", purchaseId, null));
        OrdersView view = new OrdersView(presenter);

        clickButton(view, "Checkout");

        assertTrue(containsText(view, "Checkout complete. Purchase ID: " + purchaseId));
        assertFalse(containsText(view, "Charged:"));
    }

    @Test
    void GivenOrderWithCoupon_WhenCheckoutClicked_ThenPurchaseIdIsDisplayedAndCouponClears() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(gaItem(UUID.randomUUID(), UUID.randomUUID(), 1)));
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        when(presenter.quoteCheckout("SAVE20"))
                .thenReturn(CheckoutQuoteResult.success(new BigDecimal("50.00"), new BigDecimal("40.00")));
        when(presenter.checkout("SAVE20"))
                .thenReturn(CheckoutResult.success("Checkout complete.", purchaseId, new BigDecimal("40.00")));
        OrdersView view = new OrdersView(presenter);
        assertTrue(findButton(view, "Checkout").isEnabled());
        findTextField(view, "Coupon code").setValue("SAVE20");

        clickButton(view, "Checkout");

        assertTrue(containsText(view, "Checkout complete."));
        assertTrue(containsText(view, "Charged: 40.00"));
        assertTrue(containsText(view, "Purchase ID: " + purchaseId));
        assertTrue(findOrderItemsGrid(view).getDataProvider().fetch(new Query<>()).toList().isEmpty());
        assertTrue(hasText(view, "No active order. Add tickets from the Events page."));
        assertFalse(findButton(view, "Checkout").isEnabled());
        assertEquals("", findTextField(view, "Coupon code").getValue());
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
    void GivenMemberWithNoPurchases_WhenLoadingPurchaseHistory_ThenNoPurchasesMessageIsShownAndGridIsHidden() {
        OrdersPresenter presenter = mockPresenter();
        when(presenter.currentSessionLabel()).thenReturn("Current session: Member (alice)");
        when(presenter.currentSessionState()).thenReturn(member());
        when(presenter.loadPurchaseHistory()).thenReturn(HistoryResult.success("No purchases found.", List.of()));
        OrdersView view = new OrdersView(presenter);

        clickButton(view, "Load purchase history");

        Grid<PurchaseRecordDTO> grid = findHistoryGrid(view);
        assertTrue(grid.getDataProvider().fetch(new Query<>()).toList().isEmpty());
        assertFalse(grid.isVisible());
        assertTrue(hasText(view, "No purchases found."));
        verify(presenter).loadPurchaseHistory();
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
    void GivenApplicationServiceThrows_WhenOrderActionRuns_ThenUserFacingErrorIsShown() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.failure("Could not load active order. Please try again."));
        OrdersView view = new OrdersView(presenter);

        assertTrue(hasText(view, "Could not load active order. Please try again."));
    }

    @Test
    void GivenPolicyFailure_WhenCheckoutClicked_ThenPolicyMessageIsShownInline() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String policyMessage = "Purchase policy violation: AGE_RESTRICTED - Buyer does not meet age policy";
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(gaItem(UUID.randomUUID(), UUID.randomUUID(), 1)));
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        when(presenter.quoteCheckout("")).thenReturn(CheckoutQuoteResult.success(new BigDecimal("50.00"), new BigDecimal("50.00")));
        when(presenter.checkout("")).thenReturn(CheckoutResult.failure(policyMessage));
        OrdersView view = new OrdersView(presenter);

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
        when(presenter.quoteCheckout(any())).thenReturn(CheckoutQuoteResult.success(new BigDecimal("100.00"), new BigDecimal("100.00")));
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
        when(presenter.quoteCheckout(any())).thenReturn(CheckoutQuoteResult.success(new BigDecimal("100.00"), new BigDecimal("100.00")));
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
        assertTrue(hasText(view, "Order " + orderId + " | event " + eventId + " | status ACTIVE | tickets 0 | total 0"));
        verify(presenter).updateGAQuantity(zoneId, 4);
        verify(presenter).removeItem(itemId);
    }

    @Test
    void GivenMixedOrder_WhenManagingItems_ThenGaQuantityActionIsScopedToGaItemsAndMutationsFlowThroughPresenter() {
        OrdersPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID gaZoneId = UUID.randomUUID();
        UUID gaItemId = UUID.randomUUID();
        UUID seatZoneId = UUID.randomUUID();
        UUID seatItemId = UUID.randomUUID();
        OrderItemDto gaItem = gaItem(gaItemId, gaZoneId, 2);
        OrderItemDto seat = seatItem(seatItemId, seatZoneId, UUID.randomUUID());
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(gaItem, seat));
        ActiveOrderDto afterUpdate = activeOrder(orderId, eventId, List.of(gaItem(gaItemId, gaZoneId, 5), seat));
        ActiveOrderDto afterRemove = activeOrder(orderId, eventId, List.of(seat));
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("Active order loaded.", orderId, order));
        when(presenter.updateGAQuantity(gaZoneId, 5))
                .thenReturn(OrderMutationResult.success("GA quantity updated.", null, afterUpdate));
        when(presenter.removeItem(gaItemId))
                .thenReturn(OrderMutationResult.success("Order item removed.", gaItemId, afterRemove));
        OrdersView view = new OrdersView(presenter);

        // Nothing selected: both item actions are disabled.
        assertFalse(findButton(view, "Remove selected item").isEnabled());
        assertFalse(findButton(view, "Update selected GA quantity").isEnabled());

        // Selecting the GA item enables both actions, then updating its quantity succeeds.
        findOrderItemsGrid(view).asSingleSelect().setValue(gaItem);
        assertTrue(findButton(view, "Remove selected item").isEnabled());
        assertTrue(findButton(view, "Update selected GA quantity").isEnabled());
        findIntegerField(view, "New GA quantity").setValue(5);
        clickButton(view, "Update selected GA quantity");
        assertTrue(hasText(view, "GA quantity updated."));

        // Selecting the assigned-seat row keeps Remove available but disables the GA-quantity action.
        findOrderItemsGrid(view).asSingleSelect().setValue(afterUpdate.getItems().get(1));
        assertTrue(findButton(view, "Remove selected item").isEnabled());
        assertFalse(findButton(view, "Update selected GA quantity").isEnabled());

        // Removing the GA item succeeds, leaving only the assigned seat.
        findOrderItemsGrid(view).asSingleSelect().setValue(afterUpdate.getItems().get(0));
        clickButton(view, "Remove selected item");
        assertTrue(hasText(view, "Order item removed."));
        assertEquals(List.of(seat), findOrderItemsGrid(view).getDataProvider().fetch(new Query<>()).toList());

        verify(presenter).updateGAQuantity(gaZoneId, 5);
        verify(presenter).removeItem(gaItemId);
    }

    private OrdersPresenter mockPresenter() {
        OrdersPresenter presenter = mock(OrdersPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Guest");
        when(presenter.currentSessionState()).thenReturn(guest());
        when(presenter.loadCurrentOrder()).thenReturn(OrderResult.success("No active order found.", null, null));
        when(presenter.labelsFor(any())).thenReturn(OrderLabels.empty());
        when(presenter.quoteCheckout(any())).thenReturn(CheckoutQuoteResult.success(BigDecimal.ZERO, BigDecimal.ZERO));
        when(presenter.checkOrderCompliance()).thenReturn(OrderComplianceResult.compliant("Order meets purchase requirements."));
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

    private boolean containsText(Component root, String fragment) {
        if (root instanceof HasText hasText && hasText.getText() != null && hasText.getText().contains(fragment)) {
            return true;
        }
        return root.getChildren().anyMatch(child -> containsText(child, fragment));
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

    private List<String> columnHeaders(Grid<?> grid) {
        return grid.getColumns().stream()
                .map(Grid.Column::getHeaderText)
                .toList();
    }

    @Test
    void GivenOrdersView_WhenRendered_ThenGridsShowEmptyStateMessages() {
        OrdersPresenter presenter = mockPresenter();

        OrdersView view = new OrdersView(presenter);

        for (Grid<?> grid : findGrids(view)) {
            assertTrue(grid.getEmptyStateText() != null && !grid.getEmptyStateText().isBlank(),
                    "every data grid should show an empty-state message");
        }
        assertEquals("No active order — browse events to add tickets.",
                findOrderItemsGrid(view).getEmptyStateText());
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
