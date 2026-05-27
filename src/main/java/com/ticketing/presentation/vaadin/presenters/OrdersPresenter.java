package com.ticketing.presentation.vaadin.presenters;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.dto.EventMapDTO;
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
    private static final String HISTORY_FAILURE_MESSAGE = "Could not load purchase history. Please try again.";

    private final OrderService orderService;
    private final EventService eventService;

    public OrdersPresenter(OrderService orderService, EventService eventService) {
        this.orderService = orderService;
        this.eventService = eventService;
    }

    public OrderResult createOrder(UUID eventId) {
        if (eventId == null) {
            return OrderResult.failure("Enter an event ID before creating an order.");
        }

        String token = sessionToken();
        if (token == null) {
            return OrderResult.failure(NO_SESSION_MESSAGE);
        }

        try {
            UUID orderId = orderService.createOrder(token, eventId);
            ActiveOrderDto order = orderService.getActiveOrder(token, orderId);
            return OrderResult.success("Active order created.", order.getId(), order);
        } catch (RuntimeException ex) {
            return OrderResult.failure(userMessage(ex, CREATE_FAILURE_MESSAGE));
        }
    }

    public OrderResult loadActiveOrder(UUID orderId) {
        if (orderId == null) {
            return OrderResult.failure("Enter an order ID before loading an order.");
        }

        String token = sessionToken();
        if (token == null) {
            return OrderResult.failure(NO_SESSION_MESSAGE);
        }

        try {
            ActiveOrderDto order = orderService.getActiveOrder(token, orderId);
            return OrderResult.success("Active order loaded.", order.getId(), order);
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

    public OrderMutationResult addGATickets(UUID orderId, UUID zoneId, int quantity) {
        if (orderId == null) {
            return OrderMutationResult.failure("Create or load an active order before adding tickets.");
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
            UUID itemId = orderService.addGATicketsToOrder(token, orderId, zoneId, quantity);
            ActiveOrderDto order = orderService.getActiveOrder(token, orderId);
            return OrderMutationResult.success("GA tickets added.", itemId, order);
        } catch (RuntimeException ex) {
            return OrderMutationResult.failure(userMessage(ex, ADD_GA_FAILURE_MESSAGE));
        }
    }

    public OrderMutationResult addAssignedSeat(UUID orderId, UUID zoneId, UUID seatId) {
        if (orderId == null) {
            return OrderMutationResult.failure("Create or load an active order before adding tickets.");
        }
        if (zoneId == null || seatId == null) {
            return OrderMutationResult.failure("Select a zone and seat before adding an assigned seat.");
        }

        String token = sessionToken();
        if (token == null) {
            return OrderMutationResult.failure(NO_SESSION_MESSAGE);
        }

        try {
            UUID itemId = orderService.addSeatToOrder(token, orderId, zoneId, seatId);
            ActiveOrderDto order = orderService.getActiveOrder(token, orderId);
            return OrderMutationResult.success("Assigned seat added.", itemId, order);
        } catch (RuntimeException ex) {
            return OrderMutationResult.failure(userMessage(ex, ADD_SEAT_FAILURE_MESSAGE));
        }
    }

    public OrderMutationResult removeItem(UUID orderId, UUID itemId) {
        if (orderId == null) {
            return OrderMutationResult.failure("Create or load an active order before removing items.");
        }
        if (itemId == null) {
            return OrderMutationResult.failure("Select an order item before removing it.");
        }

        String token = sessionToken();
        if (token == null) {
            return OrderMutationResult.failure(NO_SESSION_MESSAGE);
        }

        try {
            orderService.removeItemFromOrder(token, orderId, itemId);
            ActiveOrderDto order = orderService.getActiveOrder(token, orderId);
            return OrderMutationResult.success("Order item removed.", itemId, order);
        } catch (RuntimeException ex) {
            return OrderMutationResult.failure(userMessage(ex, REMOVE_FAILURE_MESSAGE));
        }
    }

    public OrderMutationResult updateGAQuantity(UUID orderId, UUID zoneId, int newQuantity) {
        if (orderId == null) {
            return OrderMutationResult.failure("Create or load an active order before updating quantities.");
        }
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
            orderService.updateGAQuantity(token, orderId, zoneId, newQuantity);
            ActiveOrderDto order = orderService.getActiveOrder(token, orderId);
            return OrderMutationResult.success("GA quantity updated.", null, order);
        } catch (RuntimeException ex) {
            return OrderMutationResult.failure(userMessage(ex, UPDATE_FAILURE_MESSAGE));
        }
    }

    public CheckoutResult checkout(UUID orderId, String couponCode) {
        if (orderId == null) {
            return CheckoutResult.failure("Create or load an active order before checkout.");
        }

        String token = sessionToken();
        if (token == null) {
            return CheckoutResult.failure(NO_SESSION_MESSAGE);
        }

        try {
            UUID purchaseId = orderService.checkout(token, orderId, blankToNull(couponCode));
            return CheckoutResult.success("Checkout complete.", purchaseId);
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

    public record CheckoutResult(boolean success, String message, UUID purchaseId) {
        public static CheckoutResult success(String message, UUID purchaseId) {
            return new CheckoutResult(true, message, purchaseId);
        }

        public static CheckoutResult failure(String message) {
            return new CheckoutResult(false, message, null);
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
