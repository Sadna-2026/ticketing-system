package com.ticketing.presentation.vaadin.views;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.ticketing.application.CardPaymentInfo;
import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.dto.OrderItemDto;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.CheckoutQuoteResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.CheckoutResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.HistoryResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderComplianceResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderLabels;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderMutationResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderResult;
import com.ticketing.presentation.vaadin.util.DestructiveActionDialogs;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
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
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

@Route(value = "orders", layout = MainLayout.class)
@PageTitle("Orders")
@SpringComponent
@UIScope
public class OrdersView extends VerticalLayout {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final OrdersPresenter presenter;

    private final Span sessionStatus = new Span();
    private final Span orderActionStatus = new Span("Order actions will report here.");
    private final Span orderStatus = new Span("No active order. Add tickets from the Events page.");
    private final Grid<OrderItemDto> orderItemsGrid = new Grid<>(OrderItemDto.class, false);
    private final IntegerField newGAQuantity = new IntegerField("New GA quantity");
    private final Button removeSelectedItem = new Button("Remove selected item");
    private final Button updateSelectedGAQuantity = new Button("Update selected GA quantity");
    private final Button clearCart = new Button("Clear cart");
    private static final String NO_TICKETS_CHECKOUT_MESSAGE = "Add tickets to your order before checkout.";

    private final TextField couponCode = new TextField("Coupon code");
    private final Span policyComplianceStatus = new Span();
    private final Span checkoutStatus = new Span("Checkout is available once the active order has tickets.");
    private Button checkoutButton;
    private final Span historyStatus = new Span("Members can load purchase history.");
    private final Paragraph memberOnlyHistoryHint = new Paragraph("Log in as a member to view purchase history.");
    private final Grid<PurchaseRecordDTO> historyGrid = new Grid<>(PurchaseRecordDTO.class, false);
    private final VerticalLayout historySection;

    private final Span lotteryWinBanner = new Span();
    private final Button goToEventsButton = new Button("Go to Events to select tickets");

    private ActiveOrderDto currentOrder;
    private OrderItemDto selectedOrderItem;
    private OrderLabels currentLabels = OrderLabels.empty();

    public OrdersView(OrdersPresenter presenter) {
        this.presenter = presenter;

        setPadding(true);
        setSpacing(true);
        setMaxWidth("1180px");
        getStyle().set("margin", "0 auto");

        configureFields();
        configureOrderItemsGrid();
        configureHistoryGrid();
        this.historySection = historySection();

        add(
                new H2("Orders"),
                new Paragraph("Review your active order, update quantities, checkout, and view purchase history. "
                        + "Browse events and add tickets on the Events page."),
                sessionStatus,
                activeOrderSection(),
                checkoutSection(),
                memberOnlyHistoryHint,
                historySection
        );
        refreshSessionStatus();
        loadActiveOrder(false);
        addAttachListener(event -> {
            refreshSessionStatus();
            loadActiveOrder(false);
        });
    }

    private void configureFields() {
        orderActionStatus.getStyle().set("white-space", "pre-line");
        policyComplianceStatus.getStyle().set("white-space", "pre-line");
        couponCode.setPlaceholder("Optional");
        couponCode.setValueChangeMode(ValueChangeMode.EAGER);
        couponCode.addValueChangeListener(event -> refreshCheckoutState());
        newGAQuantity.setMin(1);
        newGAQuantity.setValue(1);

        removeSelectedItem.setEnabled(false);
        removeSelectedItem.addClickListener(event -> removeSelectedItem());

        updateSelectedGAQuantity.setEnabled(false);
        updateSelectedGAQuantity.addClickListener(event -> updateSelectedGAQuantity());

        clearCart.setEnabled(false);
        clearCart.addClickListener(event -> clearCart());

        lotteryWinBanner.getStyle().set("color", "var(--lumo-primary-text-color)");
        lotteryWinBanner.getStyle().set("font-weight", "bold");
        lotteryWinBanner.setVisible(false);

        goToEventsButton.setVisible(false);
        goToEventsButton.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate("events")));
    }

    private void configureOrderItemsGrid() {
        orderItemsGrid.setEmptyStateText("No active order — browse events to add tickets.");
        orderItemsGrid.addColumn(this::formatZone).setHeader("Zone").setAutoWidth(true);
        orderItemsGrid.addColumn(this::formatSeat).setHeader("Seat").setAutoWidth(true);
        orderItemsGrid.addColumn(OrderItemDto::getQuantity).setHeader("Quantity").setAutoWidth(true);
        orderItemsGrid.addColumn(item -> formatPrice(item.getPricePerTicket())).setHeader("Price").setAutoWidth(true);
        orderItemsGrid.addColumn(item -> formatPrice(item.getTotalPrice())).setHeader("Line total").setAutoWidth(true);
        orderItemsGrid.addColumn(item -> item.isAssignedSeat() ? "Assigned seat" : "GA").setHeader("Type").setAutoWidth(true);
        orderItemsGrid.setAllRowsVisible(true);
        orderItemsGrid.asSingleSelect().addValueChangeListener(event -> {
            selectedOrderItem = event.getValue();
            refreshItemActionState();
        });
    }

    private void configureHistoryGrid() {
        historyGrid.setEmptyStateText("No past purchases yet — your completed orders will appear here.");
        historyGrid.addColumn(PurchaseRecordDTO::eventName).setHeader("Event").setAutoWidth(true);
        historyGrid.addColumn(PurchaseRecordDTO::companyName).setHeader("Company").setAutoWidth(true);
        historyGrid.addColumn(purchase -> formatPrice(purchase.amount())).setHeader("Amount").setAutoWidth(true);
        historyGrid.addColumn(purchase -> formatInstant(purchase.purchasedAt())).setHeader("Purchased at").setAutoWidth(true);
        historyGrid.setAllRowsVisible(true);
    }

    private VerticalLayout activeOrderSection() {
        HorizontalLayout itemActions = new HorizontalLayout(removeSelectedItem, newGAQuantity, updateSelectedGAQuantity, clearCart);
        itemActions.setAlignItems(Alignment.BASELINE);

        VerticalLayout section = new VerticalLayout(
                new H3("Active order"),
                orderActionStatus,
                orderStatus,
                lotteryWinBanner,
                goToEventsButton,
                orderItemsGrid,
                itemActions
        );
        section.setPadding(false);
        section.setWidthFull();
        section.addClassName("app-card");
        return section;
    }

    private VerticalLayout checkoutSection() {
        checkoutButton = new Button("Checkout", event -> checkout());
        checkoutButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout form = new HorizontalLayout(couponCode, checkoutButton);
        form.setAlignItems(Alignment.BASELINE);

        VerticalLayout section = new VerticalLayout(new H3("Checkout"), form, policyComplianceStatus, checkoutStatus);
        section.setPadding(false);
        section.setWidthFull();
        section.addClassName("app-card");
        return section;
    }

    private VerticalLayout historySection() {
        Button loadHistory = new Button("Load purchase history", event -> loadPurchaseHistory());
        VerticalLayout section = new VerticalLayout(new H3("Purchase history"), loadHistory, historyStatus, historyGrid);
        section.setPadding(false);
        section.setWidthFull();
        section.addClassName("app-card");
        return section;
    }

    private void loadActiveOrder() {
        loadActiveOrder(true);
    }

    private void loadActiveOrder(boolean notify) {
        applyOrderResult(presenter.loadCurrentOrder(), notify);
    }

    private void removeSelectedItem() {
        if (selectedOrderItem == null) {
            return;
        }
        DestructiveActionDialogs.confirmRemoveOrderItem(selectedOrderItemLabel(), () -> {
            UUID itemId = selectedOrderItem.getId();
            handleMutationResult(presenter.removeItem(itemId));
        });
    }

    private void updateSelectedGAQuantity() {
        UUID zoneId = selectedOrderItem == null ? null : selectedOrderItem.getZoneId();
        Integer quantity = newGAQuantity.getValue();
        handleMutationResult(presenter.updateGAQuantity(zoneId, quantity == null ? 0 : quantity));
    }

    private void clearCart() {
        String eventName = currentOrder == null ? null : formatEventLabel(currentOrder);
        DestructiveActionDialogs.confirmClearCart(eventName, () -> handleMutationResult(presenter.cancelOrder()));
    }

    private void checkout() {
        if (!canCheckout()) {
            checkoutStatus.setText(NO_TICKETS_CHECKOUT_MESSAGE);
            UiMessages.info(NO_TICKETS_CHECKOUT_MESSAGE);
            return;
        }
        buildCheckoutDialog().open();
    }

    /**
     * Payment dialog that collects the buyer's card details for the WSEP {@code pay} action.
     * Package-private so view-layer tests can drive it without opening an overlay.
     */
    Dialog buildCheckoutDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Payment");

        CheckoutQuoteResult quote = presenter.quoteCheckout(couponCode.getValue());
        Span amount = new Span(quote.success() && quote.total() != null
                ? "Amount due: " + quote.total().toPlainString()
                : "Amount due at checkout.");

        TextField currency = new TextField("Currency");
        currency.setValue("USD");
        TextField cardNumber = new TextField("Card number");
        TextField month = new TextField("Expiry month");
        TextField year = new TextField("Expiry year");
        TextField holder = new TextField("Card holder");
        TextField cvv = new TextField("CVV");
        TextField cardId = new TextField("ID");

        Span dialogStatus = new Span();
        Button pay = new Button("Pay", e -> submitPayment(dialog, dialogStatus, new CardPaymentInfo(
                currency.getValue(), cardNumber.getValue(), month.getValue(), year.getValue(),
                holder.getValue(), cvv.getValue(), cardId.getValue())));
        pay.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", e -> dialog.close());

        dialog.add(new VerticalLayout(
                new H4("Card details"), amount,
                currency, cardNumber, month, year, holder, cvv, cardId,
                new HorizontalLayout(pay, cancel), dialogStatus));
        return dialog;
    }

    private void submitPayment(Dialog dialog, Span dialogStatus, CardPaymentInfo card) {
        if (isBlank(card.cardNumber()) || isBlank(card.holder()) || isBlank(card.cvv())
                || isBlank(card.month()) || isBlank(card.year()) || isBlank(card.cardId())) {
            dialogStatus.setText("All card fields are required.");
            UiMessages.error("All card fields are required.");
            return;
        }

        CheckoutResult result = presenter.checkout(couponCode.getValue(), card);
        if (!result.success()) {
            dialogStatus.setText(result.message());
            checkoutStatus.setText(result.message());
            orderActionStatus.setText(result.message());
            UiMessages.error(result.message());
            return;
        }

        dialog.close();
        currentOrder = null;
        couponCode.clear();
        refreshOrderDisplay();
        String successMessage = formatCheckoutSuccess(result);
        checkoutStatus.setText(successMessage);
        orderActionStatus.setText(successMessage);
        UiMessages.success(successMessage);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean canCheckout() {
        return currentOrder != null && currentOrder.getItems() != null && !currentOrder.getItems().isEmpty();
    }

    private void refreshCheckoutState() {
        if (checkoutButton == null) {
            return;
        }
        boolean ready = canCheckout();
        if (!ready) {
            checkoutButton.setEnabled(false);
            policyComplianceStatus.setText("");
            checkoutStatus.setText("Checkout is available once the active order has tickets.");
            return;
        }

        OrderComplianceResult compliance = presenter.checkOrderCompliance();
        if (!compliance.success()) {
            checkoutButton.setEnabled(false);
            policyComplianceStatus.setText(compliance.message());
            checkoutStatus.setText(compliance.message());
            return;
        }
        if (!compliance.compliant()) {
            checkoutButton.setEnabled(false);
            policyComplianceStatus.setText("Purchase policy not met: " + compliance.message());
            checkoutStatus.setText("Resolve the issue above before checkout.");
            return;
        }

        checkoutButton.setEnabled(true);
        policyComplianceStatus.setText(compliance.message());

        CheckoutQuoteResult quote = presenter.quoteCheckout(couponCode.getValue());
        if (!quote.success()) {
            checkoutStatus.setText(quote.message().isBlank()
                    ? "Enter an optional coupon code, then checkout."
                    : quote.message());
            return;
        }
        checkoutStatus.setText(formatCheckoutQuote(quote));
    }

    private void loadPurchaseHistory() {
        HistoryResult result = presenter.loadPurchaseHistory();
        historyStatus.setText(result.message());
        orderActionStatus.setText(result.message());
        List<PurchaseRecordDTO> purchases = result.purchases();
        historyGrid.setItems(purchases);
        historyGrid.setVisible(!purchases.isEmpty());

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
        applyOrderResult(result, true);
    }

    private void applyOrderResult(OrderResult result, boolean notify) {
        orderActionStatus.setText(result.message());
        if (!result.success()) {
            orderStatus.setText(result.message());
            currentOrder = null;
            refreshOrderDisplay();
            if (notify) {
                UiMessages.error(result.message());
            }
            return;
        }

        UUID previousSelectionId = selectedOrderItem != null ? selectedOrderItem.getId() : null;
        currentOrder = result.order();
        refreshOrderDisplay();
        if (previousSelectionId != null && currentOrder != null) {
            currentOrder.getItems().stream()
                    .filter(item -> item.getId().equals(previousSelectionId))
                    .findFirst()
                    .ifPresent(item -> {
                        selectedOrderItem = item;
                        orderItemsGrid.select(item);
                        refreshItemActionState();
                    });
        }
        refreshSessionStatus();
        if (notify && currentOrder != null) {
            UiMessages.success(result.message());
        }
    }

    private void handleMutationResult(OrderMutationResult result) {
        orderActionStatus.setText(result.message());
        if (!result.success()) {
            UiMessages.error(result.message());
            return;
        }

        currentOrder = result.order();
        selectedOrderItem = null;
        refreshOrderDisplay();
        UiMessages.success(result.message());
    }

    private void refreshOrderDisplay() {
        currentLabels = presenter.labelsFor(currentOrder);
        List<OrderItemDto> items = currentOrder == null ? List.of() : currentOrder.getItems();
        orderItemsGrid.setItems(items);
        orderItemsGrid.setVisible(!items.isEmpty());
        selectedOrderItem = null;
        orderItemsGrid.deselectAll();
        boolean isLotteryWin = currentOrder != null && currentOrder.isLotteryWin();
        if (isLotteryWin) {
            boolean hasItems = currentOrder.getItems() != null && !currentOrder.getItems().isEmpty();
            String deadline = currentOrder.getPurchaseWindowDeadline() == null
                    ? ""
                    : " Deadline: " + DATE_TIME_FORMATTER.format(currentOrder.getPurchaseWindowDeadline()) + ".";
            if (!hasItems) {
                lotteryWinBanner.setText("You won the lottery!" + deadline
                        + " Go to the Events page to choose your tickets.");
            } else {
                lotteryWinBanner.setText("Lottery win order." + deadline
                        + " You can modify your ticket selection but cannot cancel this order.");
            }
        }
        lotteryWinBanner.setVisible(isLotteryWin);
        goToEventsButton.setVisible(isLotteryWin);
        clearCart.setEnabled(currentOrder != null && !isLotteryWin);
        refreshItemActionState();

        refreshCheckoutState();
        if (currentOrder == null) {
            orderStatus.setText("No active order. Add tickets from the Events page.");
            return;
        }

        int ticketCount = items.stream().mapToInt(OrderItemDto::getQuantity).sum();
        orderStatus.setText("Order " + currentOrder.getId()
                + " | event " + formatEventLabel(currentOrder)
                + " | status " + currentOrder.getStatus()
                + " | tickets " + ticketCount
                + " | total " + formatPrice(currentOrder.getTotalPrice()));
    }

    private String formatEventLabel(ActiveOrderDto order) {
        String name = order.getEventName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return order.getEventId().toString();
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
        boolean member = presenter.currentSessionState().loggedInMember();
        memberOnlyHistoryHint.setVisible(!member);
        historySection.setVisible(member);
    }

    private String formatZone(OrderItemDto item) {
        UUID zoneId = item.getZoneId();
        if (zoneId == null) {
            return "";
        }
        return currentLabels.zoneNames().getOrDefault(zoneId, zoneId.toString());
    }

    private String formatSeat(OrderItemDto item) {
        UUID seatId = item.getSeatId();
        if (seatId == null) {
            return "";
        }
        return currentLabels.seatLabels().getOrDefault(seatId, seatId.toString());
    }

    private String selectedOrderItemLabel() {
        if (selectedOrderItem == null) {
            return null;
        }
        String zone = formatZone(selectedOrderItem);
        String seat = formatSeat(selectedOrderItem);
        if (seat != null && !seat.isBlank()) {
            return zone + ", seat " + seat;
        }
        return zone;
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "" : DATE_TIME_FORMATTER.format(instant);
    }

    private String formatPrice(BigDecimal price) {
        return price == null ? "N/A" : "$" + price.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatCheckoutQuote(CheckoutQuoteResult quote) {
        if (quote.subtotal().compareTo(quote.total()) == 0) {
            return "Amount due: " + formatPrice(quote.total())
                    + ". Enter an optional coupon code, then checkout.";
        }
        return "Subtotal " + formatPrice(quote.subtotal())
                + " | Amount due: " + formatPrice(quote.total())
                + ". Enter an optional coupon code, then checkout.";
    }

    private String formatCheckoutSuccess(CheckoutResult result) {
        String amount = result.chargedAmount() == null
                ? ""
                : " Charged: " + formatPrice(result.chargedAmount());
        return result.message() + amount + " Purchase ID: " + result.purchaseId();
    }
}
