package com.ticketing.presentation.vaadin.views;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.OrderItemDto;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.CheckoutResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.HistoryResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.InventoryResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderMutationResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "orders", layout = MainLayout.class)
@PageTitle("Orders")
public class OrdersView extends VerticalLayout {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final OrdersPresenter presenter;

    private final Span sessionStatus = new Span();
    private final TextField eventId = new TextField("Event ID");
    private final TextField orderId = new TextField("Order ID");
    private final Span inventoryStatus = new Span("Load an event inventory map before adding tickets.");
    private final VerticalLayout inventoryDisplay = new VerticalLayout();
    private final Span orderStatus = new Span("Create or load an order to see active order details.");
    private final Grid<OrderItemDto> orderItemsGrid = new Grid<>(OrderItemDto.class, false);
    private final IntegerField newGAQuantity = new IntegerField("New GA quantity");
    private final Button removeSelectedItem = new Button("Remove selected item");
    private final Button updateSelectedGAQuantity = new Button("Update selected GA quantity");
    private final TextField couponCode = new TextField("Coupon code");
    private final Span checkoutStatus = new Span("Checkout is available once the active order has tickets.");
    private final Span historyStatus = new Span("Members can load purchase history.");
    private final Grid<PurchaseRecordDTO> historyGrid = new Grid<>(PurchaseRecordDTO.class, false);

    private ActiveOrderDto currentOrder;
    private EventMapDTO currentEventMap;
    private OrderItemDto selectedOrderItem;

    public OrdersView(OrdersPresenter presenter) {
        this.presenter = presenter;

        setPadding(true);
        setSpacing(true);
        setMaxWidth("1180px");
        getStyle().set("margin", "0 auto");

        configureFields();
        configureInventoryDisplay();
        configureOrderItemsGrid();
        configureHistoryGrid();

        add(
                new H2("Orders"),
                new Paragraph("Create an active order, reserve tickets, checkout, and review member purchase history."),
                sessionStatus,
                orderSetupSection(),
                inventorySection(),
                activeOrderSection(),
                checkoutSection(),
                historySection()
        );
        refreshSessionStatus();
        refreshOrderDisplay();
    }

    private void configureFields() {
        eventId.setPlaceholder("Published event UUID");
        orderId.setPlaceholder("Active order UUID");
        couponCode.setPlaceholder("Optional");
        newGAQuantity.setMin(1);
        newGAQuantity.setValue(1);

        removeSelectedItem.setEnabled(false);
        removeSelectedItem.addClickListener(event -> removeSelectedItem());

        updateSelectedGAQuantity.setEnabled(false);
        updateSelectedGAQuantity.addClickListener(event -> updateSelectedGAQuantity());
    }

    private void configureInventoryDisplay() {
        inventoryDisplay.setPadding(false);
        inventoryDisplay.setSpacing(true);
        inventoryDisplay.add(new Paragraph("Load an event inventory map to add GA tickets or assigned seats."));
    }

    private void configureOrderItemsGrid() {
        orderItemsGrid.addColumn(item -> item.getId().toString()).setHeader("Item ID").setAutoWidth(true);
        orderItemsGrid.addColumn(item -> labelForZone(item.getZoneId())).setHeader("Zone").setAutoWidth(true);
        orderItemsGrid.addColumn(this::formatSeat).setHeader("Seat").setAutoWidth(true);
        orderItemsGrid.addColumn(OrderItemDto::getQuantity).setHeader("Quantity").setAutoWidth(true);
        orderItemsGrid.addColumn(item -> formatPrice(item.getPricePerTicket())).setHeader("Price").setAutoWidth(true);
        orderItemsGrid.addColumn(item -> formatPrice(item.getTotalPrice())).setHeader("Line total").setAutoWidth(true);
        orderItemsGrid.addColumn(item -> item.isAssignedSeat() ? "Assigned seat" : "GA").setHeader("Type").setAutoWidth(true);
        orderItemsGrid.setMinHeight("240px");
        orderItemsGrid.asSingleSelect().addValueChangeListener(event -> {
            selectedOrderItem = event.getValue();
            refreshItemActionState();
        });
    }

    private void configureHistoryGrid() {
        historyGrid.addColumn(purchase -> purchase.purchaseId().toString()).setHeader("Purchase ID").setAutoWidth(true);
        historyGrid.addColumn(PurchaseRecordDTO::eventName).setHeader("Event").setAutoWidth(true);
        historyGrid.addColumn(PurchaseRecordDTO::companyName).setHeader("Company").setAutoWidth(true);
        historyGrid.addColumn(purchase -> formatPrice(purchase.amount())).setHeader("Amount").setAutoWidth(true);
        historyGrid.addColumn(purchase -> formatInstant(purchase.purchasedAt())).setHeader("Purchased at").setAutoWidth(true);
        historyGrid.setMinHeight("180px");
    }

    private VerticalLayout orderSetupSection() {
        Button createOrder = new Button("Create active order", event -> createOrder());
        Button loadInventory = new Button("Load event inventory", event -> loadEventInventory());
        Button loadOrder = new Button("Load active order", event -> loadActiveOrder());

        FormLayout form = new FormLayout(eventId, orderId);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("720px", 2)
        );

        HorizontalLayout actions = new HorizontalLayout(createOrder, loadInventory, loadOrder);
        actions.setAlignItems(Alignment.BASELINE);

        VerticalLayout section = new VerticalLayout(new H3("Start or load"), form, actions);
        section.setPadding(false);
        return section;
    }

    private VerticalLayout inventorySection() {
        VerticalLayout section = new VerticalLayout(
                new H3("Ticket selection"),
                inventoryStatus,
                inventoryDisplay
        );
        section.setPadding(false);
        return section;
    }

    private VerticalLayout activeOrderSection() {
        HorizontalLayout itemActions = new HorizontalLayout(removeSelectedItem, newGAQuantity, updateSelectedGAQuantity);
        itemActions.setAlignItems(Alignment.BASELINE);

        VerticalLayout section = new VerticalLayout(
                new H3("Active order"),
                orderStatus,
                orderItemsGrid,
                itemActions
        );
        section.setPadding(false);
        return section;
    }

    private VerticalLayout checkoutSection() {
        Button checkout = new Button("Checkout", event -> checkout());
        HorizontalLayout form = new HorizontalLayout(couponCode, checkout);
        form.setAlignItems(Alignment.BASELINE);

        VerticalLayout section = new VerticalLayout(new H3("Checkout"), form, checkoutStatus);
        section.setPadding(false);
        return section;
    }

    private VerticalLayout historySection() {
        Button loadHistory = new Button("Load purchase history", event -> loadPurchaseHistory());
        VerticalLayout section = new VerticalLayout(new H3("Purchase history"), loadHistory, historyStatus, historyGrid);
        section.setPadding(false);
        return section;
    }

    private void createOrder() {
        UUID parsedEventId = parseUuid(eventId, "event");
        if (parsedEventId == null) {
            return;
        }

        handleOrderResult(presenter.createOrder(parsedEventId));
    }

    private void loadActiveOrder() {
        UUID parsedOrderId = parseUuid(orderId, "order");
        if (parsedOrderId == null) {
            return;
        }

        handleOrderResult(presenter.loadActiveOrder(parsedOrderId));
    }

    private void loadEventInventory() {
        UUID parsedEventId = parseUuid(eventId, "event");
        if (parsedEventId == null) {
            return;
        }

        handleInventoryResult(presenter.loadEventInventory(parsedEventId));
    }

    private void addGATickets(UUID zoneId, Integer quantity) {
        OrderMutationResult result = presenter.addGATickets(currentOrderId(), zoneId, quantity == null ? 0 : quantity);
        handleMutationResult(result);
    }

    private void addAssignedSeat(UUID zoneId, UUID seatId) {
        OrderMutationResult result = presenter.addAssignedSeat(currentOrderId(), zoneId, seatId);
        handleMutationResult(result);
    }

    private void removeSelectedItem() {
        UUID itemId = selectedOrderItem == null ? null : selectedOrderItem.getId();
        handleMutationResult(presenter.removeItem(currentOrderId(), itemId));
    }

    private void updateSelectedGAQuantity() {
        UUID zoneId = selectedOrderItem == null ? null : selectedOrderItem.getZoneId();
        Integer quantity = newGAQuantity.getValue();
        handleMutationResult(presenter.updateGAQuantity(currentOrderId(), zoneId, quantity == null ? 0 : quantity));
    }

    private void checkout() {
        CheckoutResult result = presenter.checkout(currentOrderId(), couponCode.getValue());
        if (!result.success()) {
            checkoutStatus.setText(result.message());
            UiMessages.error(result.message());
            return;
        }

        checkoutStatus.setText(result.message() + " Purchase ID: " + result.purchaseId());
        UiMessages.success(result.message());
    }

    private void loadPurchaseHistory() {
        HistoryResult result = presenter.loadPurchaseHistory();
        historyStatus.setText(result.message());
        historyGrid.setItems(result.purchases());

        if (!result.success()) {
            UiMessages.error(result.message());
        } else if (result.memberOnly() || result.purchases().isEmpty()) {
            UiMessages.info(result.message());
        } else {
            UiMessages.success(result.message());
        }
        refreshSessionStatus();
    }

    private void handleOrderResult(OrderResult result) {
        if (!result.success()) {
            orderStatus.setText(result.message());
            UiMessages.error(result.message());
            return;
        }

        currentOrder = result.order();
        if (result.orderId() != null) {
            orderId.setValue(result.orderId().toString());
        }
        if (currentOrder != null) {
            eventId.setValue(currentOrder.getEventId().toString());
        }

        refreshOrderDisplay();
        refreshSessionStatus();
        UiMessages.success(result.message());
    }

    private void handleInventoryResult(InventoryResult result) {
        inventoryDisplay.removeAll();
        currentEventMap = result.success() ? result.eventMap() : null;
        inventoryStatus.setText(result.message());

        if (!result.success()) {
            inventoryDisplay.add(new Paragraph(result.message()));
            UiMessages.error(result.message());
            refreshOrderDisplay();
            return;
        }

        renderEventMap(result.eventMap());
        refreshOrderDisplay();
        UiMessages.success(result.message());
    }

    private void handleMutationResult(OrderMutationResult result) {
        if (!result.success()) {
            orderStatus.setText(result.message());
            UiMessages.error(result.message());
            return;
        }

        currentOrder = result.order();
        selectedOrderItem = null;
        refreshOrderDisplay();
        UiMessages.success(result.message());
    }

    private void renderEventMap(EventMapDTO eventMap) {
        inventoryDisplay.add(
                new H4(eventMap.eventName()),
                new Span("Company: " + eventMap.companyName()),
                new Span("Status: " + eventMap.status().name())
        );

        if (eventMap.zones().isEmpty()) {
            inventoryDisplay.add(new Paragraph("No inventory zones are available for this event."));
            return;
        }

        for (EventMapDTO.ZoneInfo zone : eventMap.zones()) {
            inventoryDisplay.add(zoneDetails(zone));
        }
    }

    private Details zoneDetails(EventMapDTO.ZoneInfo zone) {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(false);
        content.add(
                new Span("Type: " + zone.type().name()),
                new Span("Price: " + formatPrice(zone.pricePerTicket()))
        );

        if (zone.type() == ZoneType.GENERAL_ADMISSION) {
            IntegerField quantity = new IntegerField("Quantity");
            quantity.setMin(1);
            quantity.setValue(1);
            Button addGA = new Button("Add GA tickets", event -> addGATickets(zone.id(), quantity.getValue()));
            content.add(
                    new Span("Capacity: " + zone.maxCapacity()),
                    new Span("Available: " + zone.availableCount()),
                    new Span("Sold: " + zone.soldCount()),
                    new HorizontalLayout(quantity, addGA)
            );
        } else {
            content.add(new Span("Seats: " + zone.seats().size()));
            HorizontalLayout seats = new HorizontalLayout();
            seats.setSpacing(true);
            seats.getStyle().set("flex-wrap", "wrap");
            for (EventMapDTO.SeatInfo seat : zone.seats()) {
                Button seatButton = new Button("Add " + seat.row() + "-" + seat.seatNumber(),
                        event -> addAssignedSeat(zone.id(), seat.id()));
                seatButton.setEnabled(seat.available());
                seatButton.getStyle()
                        .set("border-radius", "999px")
                        .set("background", seat.available()
                                ? "var(--lumo-success-color-10pct)"
                                : "var(--lumo-error-color-10pct)");
                seats.add(seatButton);
            }
            content.add(seats);
        }

        Details details = new Details(zone.name(), content);
        details.setOpened(true);
        return details;
    }

    private void refreshOrderDisplay() {
        List<OrderItemDto> items = currentOrder == null ? List.of() : currentOrder.getItems();
        orderItemsGrid.setItems(items);
        selectedOrderItem = null;
        orderItemsGrid.deselectAll();
        refreshItemActionState();

        if (currentOrder == null) {
            orderStatus.setText("Create or load an order to see active order details.");
            return;
        }

        int ticketCount = items.stream().mapToInt(OrderItemDto::getQuantity).sum();
        orderStatus.setText("Order " + currentOrder.getId()
                + " | status " + currentOrder.getStatus()
                + " | tickets " + ticketCount
                + " | total " + formatPrice(currentOrder.getTotalPrice()));
    }

    private void refreshItemActionState() {
        removeSelectedItem.setEnabled(selectedOrderItem != null);
        updateSelectedGAQuantity.setEnabled(selectedOrderItem != null && !selectedOrderItem.isAssignedSeat());
        if (selectedOrderItem != null && !selectedOrderItem.isAssignedSeat()) {
            newGAQuantity.setValue(selectedOrderItem.getQuantity());
        }
    }

    private void refreshSessionStatus() {
        sessionStatus.setText(presenter.currentSessionLabel());
    }

    private UUID currentOrderId() {
        return currentOrder == null ? null : currentOrder.getId();
    }

    private UUID parseUuid(TextField field, String label) {
        String value = field.getValue();
        if (value == null || value.isBlank()) {
            UiMessages.error("Enter a " + label + " ID.");
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            UiMessages.error("Enter a valid " + label + " ID.");
            return null;
        }
    }

    private String labelForZone(UUID zoneId) {
        if (zoneId == null) {
            return "";
        }
        if (currentEventMap == null) {
            return zoneId.toString();
        }
        return currentEventMap.zones().stream()
                .filter(zone -> zone.id().equals(zoneId))
                .findFirst()
                .map(zone -> zone.name() + " (" + zoneId + ")")
                .orElse(zoneId.toString());
    }

    private String formatSeat(OrderItemDto item) {
        if (item.getSeatId() == null) {
            return "";
        }
        if (currentEventMap == null) {
            return item.getSeatId().toString();
        }
        return currentEventMap.zones().stream()
                .flatMap(zone -> zone.seats().stream())
                .filter(seat -> seat.id().equals(item.getSeatId()))
                .findFirst()
                .map(seat -> seat.row() + "-" + seat.seatNumber())
                .orElse(item.getSeatId().toString());
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "" : DATE_TIME_FORMATTER.format(instant);
    }

    private String formatPrice(BigDecimal price) {
        return price == null ? "N/A" : price.toPlainString();
    }
}
