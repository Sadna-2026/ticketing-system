package com.ticketing.presentation.vaadin.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.OrderItemDto;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.services.EventQueryService;
import com.ticketing.application.services.OrderService;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.CheckoutResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.HistoryResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.InventoryResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderMutationResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderResult;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.server.VaadinSession;

@DisplayName("OrdersPresenter")
class OrdersPresenterTest {

    private OrderService orderService;
    private EventQueryService eventQueryService;
    private OrdersPresenter presenter;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        eventQueryService = mock(EventQueryService.class);
        presenter = new OrdersPresenter(orderService, eventQueryService);
        installVaadinSession();
    }

    @AfterEach
    void tearDown() {
        VaadinSession.setCurrent(null);
    }

    @Test
    void GivenGuestSessionAndEventId_WhenCreatingActiveOrder_ThenOrderServiceCreatesOrderAndSummaryCanLoad() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of());
        SessionContext.setSessionToken("guest-token");
        when(orderService.createOrder("guest-token", eventId)).thenReturn(orderId);
        when(orderService.getActiveOrder("guest-token", orderId)).thenReturn(order);

        OrderResult result = presenter.createOrder(eventId);

        assertTrue(result.success());
        assertEquals("Active order created.", result.message());
        assertEquals(orderId, result.orderId());
        assertSame(order, result.order());
        verify(orderService).createOrder("guest-token", eventId);
        verify(orderService).getActiveOrder("guest-token", orderId);
    }

    @Test
    void GivenActiveOrderAndTicketSelection_WhenAddingGaAndAssignedTickets_ThenOrderServiceAddsTicketsAndViewRefreshesSummary() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID gaZoneId = UUID.randomUUID();
        UUID seatZoneId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID gaItemId = UUID.randomUUID();
        UUID seatItemId = UUID.randomUUID();
        ActiveOrderDto gaOrder = activeOrder(orderId, eventId, List.of(gaItem(gaItemId, gaZoneId, 2)));
        ActiveOrderDto seatOrder = activeOrder(orderId, eventId, List.of(
                gaItem(gaItemId, gaZoneId, 2),
                seatItem(seatItemId, seatZoneId, seatId)));
        SessionContext.setSessionToken("guest-token");
        when(orderService.addGATicketsToOrder("guest-token", orderId, gaZoneId, 2)).thenReturn(gaItemId);
        when(orderService.addSeatToOrder("guest-token", orderId, seatZoneId, seatId)).thenReturn(seatItemId);
        when(orderService.getActiveOrder("guest-token", orderId)).thenReturn(gaOrder, seatOrder);

        OrderMutationResult gaResult = presenter.addGATickets(orderId, gaZoneId, 2);
        OrderMutationResult seatResult = presenter.addAssignedSeat(orderId, seatZoneId, seatId);

        assertTrue(gaResult.success());
        assertEquals("GA tickets added.", gaResult.message());
        assertEquals(gaItemId, gaResult.itemId());
        assertSame(gaOrder, gaResult.order());
        assertTrue(seatResult.success());
        assertEquals("Assigned seat added.", seatResult.message());
        assertEquals(seatItemId, seatResult.itemId());
        assertSame(seatOrder, seatResult.order());
        verify(orderService).addGATicketsToOrder("guest-token", orderId, gaZoneId, 2);
        verify(orderService).addSeatToOrder("guest-token", orderId, seatZoneId, seatId);
    }

    @Test
    void GivenOrderId_WhenViewingActiveOrder_ThenItemsAndTotalAreReturned() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(gaItem(UUID.randomUUID(), UUID.randomUUID(), 3)));
        SessionContext.setSessionToken("guest-token");
        when(orderService.getActiveOrder("guest-token", orderId)).thenReturn(order);

        OrderResult result = presenter.loadActiveOrder(orderId);

        assertTrue(result.success());
        assertEquals("Active order loaded.", result.message());
        assertEquals(1, result.order().getItems().size());
        assertEquals(new BigDecimal("150.00"), result.order().getTotalPrice());
        verify(orderService).getActiveOrder("guest-token", orderId);
    }

    @Test
    void GivenOrderWithCoupon_WhenCheckingOut_ThenCheckoutReturnsPurchaseIdAndSuccessMessage() {
        UUID orderId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        SessionContext.setSessionToken("guest-token");
        when(orderService.checkout("guest-token", orderId, "SAVE20")).thenReturn(purchaseId);

        CheckoutResult result = presenter.checkout(orderId, " SAVE20 ");

        assertTrue(result.success());
        assertEquals("Checkout complete.", result.message());
        assertEquals(purchaseId, result.purchaseId());
        verify(orderService).checkout("guest-token", orderId, "SAVE20");
    }

    @Test
    void GivenMemberSession_WhenLoadingPurchaseHistory_ThenPurchaseRecordsAreDisplayed() {
        UUID memberId = UUID.randomUUID();
        PurchaseRecordDTO purchase = new PurchaseRecordDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Spring Concert",
                "Acme",
                memberId,
                "TXN-1",
                new BigDecimal("80.00"),
                Instant.parse("2026-05-26T12:00:00Z"));
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(memberId);
        SessionContext.setUsername("alice");
        when(orderService.getPurchaseHistory("member-token")).thenReturn(List.of(purchase));

        HistoryResult result = presenter.loadPurchaseHistory();

        assertTrue(result.success());
        assertFalse(result.memberOnly());
        assertEquals("Loaded 1 purchase(s).", result.message());
        assertEquals(List.of(purchase), result.purchases());
        verify(orderService).getPurchaseHistory("member-token");
    }

    @Test
    void GivenApplicationServiceThrows_WhenOrderActionRuns_ThenUserFacingErrorIsShown() {
        UUID eventId = UUID.randomUUID();
        SessionContext.setSessionToken("guest-token");
        when(orderService.createOrder("guest-token", eventId))
                .thenThrow(new IllegalStateException("Session already has an active order"));

        OrderResult result = presenter.createOrder(eventId);

        assertFalse(result.success());
        assertEquals("Session already has an active order", result.message());
        assertNull(result.order());
    }

    @Test
    void GivenSelectedItemAndGaQuantity_WhenRemovingAndUpdating_ThenSupportedOrderActionsAreCalled() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        ActiveOrderDto order = activeOrder(orderId, eventId, List.of(gaItem(itemId, zoneId, 4)));
        SessionContext.setSessionToken("guest-token");
        when(orderService.getActiveOrder("guest-token", orderId)).thenReturn(order);

        OrderMutationResult removeResult = presenter.removeItem(orderId, itemId);
        OrderMutationResult updateResult = presenter.updateGAQuantity(orderId, zoneId, 4);

        assertTrue(removeResult.success());
        assertEquals("Order item removed.", removeResult.message());
        assertTrue(updateResult.success());
        assertEquals("GA quantity updated.", updateResult.message());
        verify(orderService).removeItemFromOrder("guest-token", orderId, itemId);
        verify(orderService).updateGAQuantity("guest-token", orderId, zoneId, 4);
    }

    @Test
    void GivenEventId_WhenLoadingInventory_ThenEventQueryServiceIsCalledWithoutSessionToken() {
        UUID eventId = UUID.randomUUID();
        EventMapDTO eventMap = eventMap(eventId);
        when(eventQueryService.getEventMap(eventId)).thenReturn(Optional.of(eventMap));

        InventoryResult result = presenter.loadEventInventory(eventId);

        assertTrue(result.success());
        assertSame(eventMap, result.eventMap());
        verify(eventQueryService).getEventMap(eventId);
        verifyNoInteractions(orderService);
    }

    @Test
    void GivenNoSession_WhenCreatingOrder_ThenUserIsToldToStartSessionAndNoServiceIsCalled() {
        OrderResult result = presenter.createOrder(UUID.randomUUID());

        assertFalse(result.success());
        assertEquals("Start a guest or member session before managing orders.", result.message());
        verifyNoInteractions(orderService);
    }

    @Test
    void GivenGuestSession_WhenLoadingPurchaseHistory_ThenMemberOnlyInfoIsReturned() {
        SessionContext.setSessionToken("guest-token");

        HistoryResult result = presenter.loadPurchaseHistory();

        assertTrue(result.success());
        assertTrue(result.memberOnly());
        assertEquals("Purchase history is available for members only.", result.message());
        assertTrue(result.purchases().isEmpty());
        verifyNoInteractions(orderService);
    }

    private void installVaadinSession() {
        VaadinSession session = mock(VaadinSession.class);
        Map<String, Object> attributes = new java.util.HashMap<>();

        doAnswer(invocation -> {
            attributes.put(invocation.getArgument(0, String.class), invocation.getArgument(1));
            return null;
        }).when(session).setAttribute(anyString(), nullable(Object.class));

        when(session.getAttribute(anyString())).thenAnswer(invocation ->
                attributes.get(invocation.getArgument(0, String.class))
        );

        VaadinSession.setCurrent(session);
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

    private static EventMapDTO eventMap(UUID eventId) {
        UUID gaZoneId = UUID.randomUUID();
        UUID seatZoneId = UUID.randomUUID();
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
                                List.of(new EventMapDTO.SeatInfo(UUID.randomUUID(), "A", "1", true)))
                ));
    }
}
