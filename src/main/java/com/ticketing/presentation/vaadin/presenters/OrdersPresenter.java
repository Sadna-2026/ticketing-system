package com.ticketing.presentation.vaadin.presenters;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketing.application.CardPaymentInfo;
import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.OrderItemDto;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.services.EventService;
import com.ticketing.application.services.OrderService;
import com.ticketing.presentation.vaadin.util.SessionContext;

@Component
public class OrdersPresenter {

    private static final Logger logger = LoggerFactory.getLogger(OrdersPresenter.class);

    private static final String NO_SESSION_MESSAGE = "Start a guest or member session before managing orders.";
    private static final String CREATE_FAILURE_MESSAGE = "Could not create active order. Please try again.";
    private static final String LOAD_FAILURE_MESSAGE = "Could not load active order. Please try again.";
    private static final String INVENTORY_FAILURE_MESSAGE = "Could not load event inventory. Please try again.";
    private static final String ADD_GA_FAILURE_MESSAGE = "Could not add GA tickets. Please try again.";
    private static final String ADD_SEAT_FAILURE_MESSAGE = "Could not add assigned seat. Please try again.";
    private static final String REMOVE_FAILURE_MESSAGE = "Could not remove order item. Please try again.";
    private static final String UPDATE_FAILURE_MESSAGE = "Could not update GA quantity. Please try again.";
    private static final String CHECKOUT_FAILURE_MESSAGE = "Could not checkout. Please try again.";
    private static final String CLEAR_FAILURE_MESSAGE = "Could not clear the cart. Please try again.";
    private static final String HISTORY_FAILURE_MESSAGE = "Could not load purchase history. Please try again.";

    private final OrderService orderService;
    private final EventService eventService;

    public OrdersPresenter(OrderService orderService, EventService eventService) {
        this.orderService = orderService;
        this.eventService = eventService;
    }

    public OrderResult loadCurrentOrder() {
        String token = sessionToken();
        if (token == null) {
            return OrderResult.failure(NO_SESSION_MESSAGE);
        }

        try {
            ActiveOrderDto order = orderService.getActiveOrder(token);
            if (order == null) {
                return OrderResult.success("No active order found.", null, null);
            }
            return OrderResult.success("Active order loaded.", order.getId(), enrichOrder(order));
        } catch (RuntimeException ex) {
            return OrderResult.failure(userMessage(ex, LOAD_FAILURE_MESSAGE));
        }
    }

    public InventoryResult loadEventInventory(UUID eventId) {
        if (eventId == null) {
            return InventoryResult.failure("Enter an event ID before loading inventory.");
        }

        try {
            return eventService.getEventMap(eventId)
                    .map(eventMap -> InventoryResult.success("Event inventory loaded.", eventMap))
                    .orElseGet(() -> InventoryResult.failure("Event inventory not found."));
        } catch (RuntimeException ex) {
            logger.warn(INVENTORY_FAILURE_MESSAGE, ex);
            return InventoryResult.failure(INVENTORY_FAILURE_MESSAGE);
        }
    }

    /**
     * Resolves human-readable zone names and seat labels for the items in an order,
     * so the cart can display them instead of raw UUIDs while keeping the ids internal.
     * Returns empty maps when the order is null or its event map can't be loaded — the
     * cart then falls back to the UUID string, so the order flow still works.
     */
    public OrderLabels labelsFor(ActiveOrderDto order) {
        if (order == null || order.getItems().isEmpty()) {
            return OrderLabels.empty();
        }
        try {
            return eventService.getEventMap(order.getEventId())
                    .map(eventMap -> buildLabels(eventMap, order.getItems()))
                    .orElseGet(OrderLabels::empty);
        } catch (RuntimeException ex) {
            logger.warn("Could not resolve zone/seat labels for active order {}", order.getId(), ex);
            return OrderLabels.empty();
        }
    }

    /** Resolves labels only for the zones/seats actually referenced by the order's items. */
    private static OrderLabels buildLabels(EventMapDTO eventMap, List<OrderItemDto> items) {
        Set<UUID> neededZones = new HashSet<>();
        Set<UUID> neededSeats = new HashSet<>();
        for (OrderItemDto item : items) {
            if (item.getZoneId() != null) {
                neededZones.add(item.getZoneId());
            }
            if (item.getSeatId() != null) {
                neededSeats.add(item.getSeatId());
            }
        }

        Map<UUID, String> zoneNames = new HashMap<>();
        Map<UUID, String> seatLabels = new HashMap<>();
        for (EventMapDTO.ZoneInfo zone : eventMap.zones()) {
            if (neededZones.contains(zone.id())) {
                zoneNames.put(zone.id(), zone.name());
            }
            if (neededSeats.isEmpty()) {
                continue;
            }
            for (EventMapDTO.SeatInfo seat : zone.seats()) {
                if (neededSeats.contains(seat.id())) {
                    seatLabels.put(seat.id(), seat.row() + "-" + seat.seatNumber());
                }
            }
        }
        return new OrderLabels(zoneNames, seatLabels);
    }

    public OrderMutationResult addGATickets(UUID eventId, UUID zoneId, int quantity) {
        if (eventId == null) {
            return OrderMutationResult.failure("Enter an event ID before adding tickets.");
        }
        if (zoneId == null) {
            return OrderMutationResult.failure("Select a GA zone before adding tickets.");
        }
        if (quantity <= 0) {
            return OrderMutationResult.failure("GA ticket quantity must be positive.");
        }

        String token = sessionToken();
        if (token == null) {
            return OrderMutationResult.failure(NO_SESSION_MESSAGE);
        }

        try {
            UUID itemId = orderService.addGATicketsToOrder(token, eventId, zoneId, quantity);
            ActiveOrderDto order = enrichOrder(orderService.getActiveOrder(token));
            return OrderMutationResult.success("GA tickets added.", itemId, order);
        } catch (RuntimeException ex) {
            return OrderMutationResult.failure(userMessage(ex, ADD_GA_FAILURE_MESSAGE));
        }
    }

    public OrderMutationResult addAssignedSeat(UUID eventId, UUID zoneId, UUID seatId) {
        return addAssignedSeats(eventId, zoneId, List.of(seatId));
    }

    public OrderMutationResult addAssignedSeats(UUID eventId, UUID zoneId, List<UUID> seatIds) {
        if (eventId == null) {
            return OrderMutationResult.failure("Enter an event ID before adding tickets.");
        }
        if (zoneId == null) {
            return OrderMutationResult.failure("Select a zone before adding seats.");
        }
        if (seatIds == null || seatIds.isEmpty()) {
            return OrderMutationResult.failure("Select at least one seat on the map, then click Add selected seats.");
        }

        String token = sessionToken();
        if (token == null) {
            return OrderMutationResult.failure(NO_SESSION_MESSAGE);
        }

        try {
            List<com.ticketing.application.SelectionRequest.SeatPick> picks = seatIds.stream()
                    .map(seatId -> new com.ticketing.application.SelectionRequest.SeatPick(zoneId, seatId))
                    .toList();
            orderService.addSelectionToOrder(token,
                    new com.ticketing.application.SelectionRequest(eventId, picks, List.of()));
            ActiveOrderDto order = enrichOrder(orderService.getActiveOrder(token));
            int count = seatIds.size();
            String message = count == 1 ? "Assigned seat added." : count + " assigned seats added.";
            return OrderMutationResult.success(message, null, order);
        } catch (RuntimeException ex) {
            return OrderMutationResult.failure(userMessage(ex, ADD_SEAT_FAILURE_MESSAGE));
        }
    }

    public OrderMutationResult removeItem(UUID itemId) {
        if (itemId == null) {
            return OrderMutationResult.failure("Select an order item before removing it.");
        }

        String token = sessionToken();
        if (token == null) {
            return OrderMutationResult.failure(NO_SESSION_MESSAGE);
        }

        try {
            orderService.removeItemFromOrder(token, itemId);
            ActiveOrderDto order = enrichOrder(orderService.getActiveOrder(token));
            return OrderMutationResult.success("Order item removed.", itemId, order);
        } catch (RuntimeException ex) {
            return OrderMutationResult.failure(userMessage(ex, REMOVE_FAILURE_MESSAGE));
        }
    }

    public OrderMutationResult updateGAQuantity(UUID zoneId, int newQuantity) {
        if (zoneId == null) {
            return OrderMutationResult.failure("Select a GA order item before updating quantity.");
        }
        if (newQuantity <= 0) {
            return OrderMutationResult.failure("GA ticket quantity must be positive.");
        }

        String token = sessionToken();
        if (token == null) {
            return OrderMutationResult.failure(NO_SESSION_MESSAGE);
        }

        try {
            orderService.updateGAQuantity(token, zoneId, newQuantity);
            ActiveOrderDto order = enrichOrder(orderService.getActiveOrder(token));
            return OrderMutationResult.success("GA quantity updated.", null, order);
        } catch (RuntimeException ex) {
            return OrderMutationResult.failure(userMessage(ex, UPDATE_FAILURE_MESSAGE));
        }
    }

    /**
     * Clears the active order (releases its locked inventory) so the buyer isn't
     * stuck with an order pinned to one event. Surfaces the domain reason (e.g. no
     * active order) on failure.
     */
    public OrderMutationResult cancelOrder() {
        String token = sessionToken();
        if (token == null) {
            return OrderMutationResult.failure(NO_SESSION_MESSAGE);
        }

        try {
            orderService.cancelOrder(token);
            return OrderMutationResult.success("Cart cleared.", null, null);
        } catch (RuntimeException ex) {
            return OrderMutationResult.failure(userMessage(ex, CLEAR_FAILURE_MESSAGE));
        }
    }

    public CheckoutQuoteResult quoteCheckout(String couponCode) {
        String token = sessionToken();
        if (token == null) {
            return CheckoutQuoteResult.failure(NO_SESSION_MESSAGE);
        }

        try {
            OrderService.CheckoutQuote quote = orderService.quoteCheckout(token, blankToNull(couponCode));
            return CheckoutQuoteResult.success(quote.subtotal(), quote.total());
        } catch (RuntimeException ex) {
            return CheckoutQuoteResult.failure(userMessage(ex, CHECKOUT_FAILURE_MESSAGE));
        }
    }

    public OrderComplianceResult checkOrderCompliance() {
        String token = sessionToken();
        if (token == null) {
            return OrderComplianceResult.failure(NO_SESSION_MESSAGE);
        }

        try {
            OrderService.PurchasePolicyStatus status = orderService.checkPurchasePolicy(token);
            if (status.compliant()) {
                return OrderComplianceResult.compliant("Order meets purchase requirements.");
            }
            return OrderComplianceResult.nonCompliant(status.reason());
        } catch (RuntimeException ex) {
            return OrderComplianceResult.failure(userMessage(ex, CHECKOUT_FAILURE_MESSAGE));
        }
    }

    public CheckoutResult checkout(String couponCode) {
        return checkout(couponCode, null);
    }

    public CheckoutResult checkout(String couponCode, CardPaymentInfo card) {
        String token = sessionToken();
        if (token == null) {
            return CheckoutResult.failure(NO_SESSION_MESSAGE);
        }

        try {
            OrderService.CheckoutCompletion completion = orderService.checkout(token, blankToNull(couponCode), card);
            return CheckoutResult.success(
                    "Checkout complete.", completion.purchaseId(), completion.chargedAmount());
        } catch (RuntimeException ex) {
            return CheckoutResult.failure(userMessage(ex, CHECKOUT_FAILURE_MESSAGE));
        }
    }

    public HistoryResult loadPurchaseHistory() {
        String token = sessionToken();
        if (token == null) {
            return HistoryResult.failure(NO_SESSION_MESSAGE);
        }
        if (!SessionContext.isLoggedInMember()) {
            return HistoryResult.memberOnly("Purchase history is available for members only.");
        }

        try {
            List<PurchaseRecordDTO> purchases = orderService.getPurchaseHistory(token);
            if (purchases.isEmpty()) {
                return HistoryResult.success("No purchases found.", purchases);
            }
            return HistoryResult.success("Loaded " + purchases.size() + " purchase(s).", purchases);
        } catch (RuntimeException ex) {
            return HistoryResult.failure(userMessage(ex, HISTORY_FAILURE_MESSAGE));
        }
    }

    public String currentSessionLabel() {
        return SessionContext.currentSessionLabel();
    }

    public SessionContext.UiState currentSessionState() {
        return SessionContext.currentUiState();
    }

    private static String sessionToken() {
        String token = SessionContext.getSessionToken();
        return token == null || token.isBlank() ? null : token;
    }

    private ActiveOrderDto enrichOrder(ActiveOrderDto order) {
        if (order == null) {
            return null;
        }
        if (order.getEventName() != null && !order.getEventName().isBlank()) {
            return order;
        }
        try {
            return eventService.getEventMap(order.getEventId())
                    .map(map -> new ActiveOrderDto(
                            order.getId(),
                            order.getSessionId(),
                            order.getMemberId(),
                            order.getEventId(),
                            order.getCreatedAt(),
                            order.getStatus(),
                            order.getItems(),
                            order.getTotalPrice(),
                            map.eventName(),
                            order.isLotteryWin(),
                            order.getPurchaseWindowDeadline()))
                    .orElse(order);
        } catch (RuntimeException ex) {
            logger.warn("Could not resolve event name for active order {}", order.getId(), ex);
            return order;
        }
    }

    private String userMessage(RuntimeException ex, String fallback) {
        if (ex instanceof IllegalArgumentException
                || ex instanceof IllegalStateException
                || ex instanceof SecurityException) {
            String message = ex.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
        }

        logger.warn(fallback, ex);
        return fallback;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record OrderResult(boolean success, String message, UUID orderId, ActiveOrderDto order) {
        public static OrderResult success(String message, UUID orderId, ActiveOrderDto order) {
            return new OrderResult(true, message, orderId, order);
        }

        public static OrderResult failure(String message) {
            return new OrderResult(false, message, null, null);
        }
    }

    /** Human-readable cart labels resolved from an event map; ids stay internal. */
    public record OrderLabels(Map<UUID, String> zoneNames, Map<UUID, String> seatLabels) {
        public OrderLabels {
            zoneNames = zoneNames == null ? Map.of() : Map.copyOf(zoneNames);
            seatLabels = seatLabels == null ? Map.of() : Map.copyOf(seatLabels);
        }

        public static OrderLabels empty() {
            return new OrderLabels(Map.of(), Map.of());
        }
    }

    public record InventoryResult(boolean success, String message, EventMapDTO eventMap) {
        public static InventoryResult success(String message, EventMapDTO eventMap) {
            return new InventoryResult(true, message, eventMap);
        }

        public static InventoryResult failure(String message) {
            return new InventoryResult(false, message, null);
        }
    }

    public record OrderMutationResult(boolean success, String message, UUID itemId, ActiveOrderDto order) {
        public static OrderMutationResult success(String message, UUID itemId, ActiveOrderDto order) {
            return new OrderMutationResult(true, message, itemId, order);
        }

        public static OrderMutationResult failure(String message) {
            return new OrderMutationResult(false, message, null, null);
        }
    }

    public record CheckoutQuoteResult(boolean success, String message, BigDecimal subtotal, BigDecimal total) {
        public static CheckoutQuoteResult success(BigDecimal subtotal, BigDecimal total) {
            return new CheckoutQuoteResult(true, "", subtotal, total);
        }

        public static CheckoutQuoteResult failure(String message) {
            return new CheckoutQuoteResult(false, message, null, null);
        }
    }

    public record OrderComplianceResult(boolean success, boolean compliant, String message) {
        public static OrderComplianceResult compliant(String message) {
            return new OrderComplianceResult(true, true, message);
        }

        public static OrderComplianceResult nonCompliant(String message) {
            return new OrderComplianceResult(true, false, message);
        }

        public static OrderComplianceResult failure(String message) {
            return new OrderComplianceResult(false, false, message);
        }
    }

    public record CheckoutResult(boolean success, String message, UUID purchaseId, BigDecimal chargedAmount) {
        public static CheckoutResult success(String message, UUID purchaseId, BigDecimal chargedAmount) {
            return new CheckoutResult(true, message, purchaseId, chargedAmount);
        }

        public static CheckoutResult failure(String message) {
            return new CheckoutResult(false, message, null, null);
        }
    }

    public record HistoryResult(boolean success, String message, List<PurchaseRecordDTO> purchases, boolean memberOnly) {
        public HistoryResult {
            purchases = purchases == null ? List.of() : List.copyOf(purchases);
        }

        public static HistoryResult success(String message, List<PurchaseRecordDTO> purchases) {
            return new HistoryResult(true, message, purchases, false);
        }

        public static HistoryResult memberOnly(String message) {
            return new HistoryResult(true, message, List.of(), true);
        }

        public static HistoryResult failure(String message) {
            return new HistoryResult(false, message, List.of(), false);
        }
    }
}
