package com.ticketing.presentation.vaadin.views;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.dto.CompanySummaryDTO;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.LayoutCellType;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.components.PolicyBadgesPanel;
import com.ticketing.presentation.vaadin.components.SeatMapComponent;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.MapResult;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.SearchResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderMutationResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderResult;
import com.ticketing.presentation.vaadin.presenters.QueuePresenter;
import com.ticketing.presentation.vaadin.presenters.QueuePresenter.QueueResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

@Route(value = "events", layout = MainLayout.class)
@PageTitle("Events")
@SpringComponent
@UIScope
public class EventsView extends VerticalLayout implements BeforeEnterObserver {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final EventsPresenter presenter;
    private final OrdersPresenter ordersPresenter;
    private final QueuePresenter queuePresenter;

    private final TextField text = new TextField("Search text");
    private final TextField region = new TextField("Region");
    private final ComboBox<CompanySummaryDTO> companyName = new ComboBox<>("Company");
    private final ComboBox<EventCategory> category = new ComboBox<>("Category");
    private final BigDecimalField minPrice = new BigDecimalField("Min price");
    private final BigDecimalField maxPrice = new BigDecimalField("Max price");
    private final DatePicker fromDate = new DatePicker("From date");
    private final DatePicker toDate = new DatePicker("To date");
    private final Span sessionStatus = new Span();
    private final Span activeOrderStatus = new Span();
    private final Span resultsStatus = new Span("Search for events to see results.");
    private final Grid<EventSummaryDTO> resultsGrid = new Grid<>(EventSummaryDTO.class, false);
    private final Button viewMap = new Button("Select tickets");

    private EventSummaryDTO selectedEvent;
    private UUID directlyAdmittedEventId;
    private UUID pendingEventId;

    public EventsView(EventsPresenter presenter, OrdersPresenter ordersPresenter, QueuePresenter queuePresenter) {
        this.presenter = presenter;
        this.ordersPresenter = ordersPresenter;
        this.queuePresenter = queuePresenter;

        setPadding(true);
        setSpacing(true);
        setMaxWidth("1100px");
        getStyle().set("margin", "0 auto");

        configureFields();
        configureResultsGrid();

        add(
                new H2("Events"),
                new Paragraph("Search published events, inspect venue inventory, and add tickets to your active order. "
                        + "Manage and checkout your cart on the Orders page."),
                sessionStatus,
                activeOrderStatus,
                searchSection(),
                new H3("Search results"),
                resultsStatus,
                resultsGrid,
                viewMap);
        refreshSessionStatus();
        refreshActiveOrderStatus();
        addAttachListener(event -> {
            refreshSessionStatus();
            refreshActiveOrderStatus();
            if (pendingEventId != null) {
                loadEventById(pendingEventId);
                pendingEventId = null;
            }
        });
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        List<String> eventIdParam = event.getLocation().getQueryParameters()
                .getParameters().getOrDefault("eventId", List.of());
        if (!eventIdParam.isEmpty()) {
            try {
                pendingEventId = UUID.fromString(eventIdParam.get(0).trim());
            } catch (IllegalArgumentException ignored) {
                pendingEventId = null;
            }
        } else {
            pendingEventId = null;
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        releaseQueueSlot();
        super.onDetach(detachEvent);
    }

    private void configureFields() {
        category.setItems(EventCategory.values());
        category.setItemLabelGenerator(this::formatCategory);

        text.setPlaceholder("Event, artist, or description");
        region.setPlaceholder("Exact region");
        companyName.setPlaceholder("Search by company name");
        companyName.setItemLabelGenerator(CompanySummaryDTO::name);
        companyName.setClearButtonVisible(true);
        List<CompanySummaryDTO> companies = presenter.searchCompanies("");
        companyName.setItems(companies == null ? List.of() : companies);

        viewMap.setEnabled(false);
        viewMap.addClickListener(event -> loadSelectedEventMap());
    }

    private void configureResultsGrid() {
        resultsGrid.setEmptyStateText("No events match your search yet — adjust the filters and search again.");
        resultsGrid.addColumn(EventSummaryDTO::name).setHeader("Event").setAutoWidth(true);
        resultsGrid.addColumn(event -> formatCategory(event.category())).setHeader("Category").setAutoWidth(true);
        resultsGrid.addColumn(event -> formatInstant(event.schedule().getStartTime())).setHeader("Starts")
                .setAutoWidth(true);
        resultsGrid.addColumn(event -> event.status().name()).setHeader("Status").setAutoWidth(true);
        resultsGrid.setMinHeight("240px");

        resultsGrid.asSingleSelect().addValueChangeListener(event -> {
            selectedEvent = event.getValue();
            viewMap.setEnabled(selectedEvent != null);
        });
    }

    private VerticalLayout searchSection() {
        Button search = new Button("Search events", event -> searchEvents());
        Button clear = new Button("Clear filters", event -> clearFilters());

        FormLayout form = new FormLayout(
                text,
                region,
                companyName,
                category,
                minPrice,
                maxPrice,
                fromDate,
                toDate);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("640px", 2),
                new FormLayout.ResponsiveStep("960px", 4));

        HorizontalLayout actions = new HorizontalLayout(search, clear);
        actions.setAlignItems(Alignment.BASELINE);

        VerticalLayout section = new VerticalLayout(form, actions);
        section.setPadding(false);
        return section;
    }

    private void searchEvents() {
        SearchResult result = presenter.searchEvents(
                text.getValue(),
                region.getValue(),
                category.getValue(),
                companyName.getValue() == null ? null : companyName.getValue().name(),
                minPrice.getValue(),
                maxPrice.getValue(),
                fromDate.getValue(),
                toDate.getValue());

        selectedEvent = null;
        viewMap.setEnabled(false);

        if (!result.success()) {
            resultsGrid.setItems(List.of());
            resultsStatus.setText(result.message());
            UiMessages.error(result.message());
            return;
        }

        resultsGrid.setItems(result.events());
        resultsStatus.setText(result.message());
        if (result.empty()) {
            UiMessages.info(result.message());
        } else {
            UiMessages.success(result.message());
        }
    }

    private void clearFilters() {
        text.clear();
        region.clear();
        companyName.clear();
        category.clear();
        minPrice.clear();
        maxPrice.clear();
        fromDate.clear();
        toDate.clear();
        resultsGrid.setItems(List.of());
        selectedEvent = null;
        viewMap.setEnabled(false);
        resultsStatus.setText("Search for events to see results.");
        UiMessages.info("Filters cleared.");
    }

    private void loadSelectedEventMap() {
        if (selectedEvent == null) {
            UiMessages.error("Select an event from the results first.");
            return;
        }
        UUID eventId = selectedEvent.id();

        if (!eventId.equals(directlyAdmittedEventId)) {
            releaseQueueSlot();
            QueueResult gate = queuePresenter.enterQueueGate(eventId);
            if (gate.queued()) {
                UiMessages.info("This event is under high load — redirecting you to the virtual queue.");
                getUI().ifPresent(ui -> ui.navigate("queue?eventId=" + eventId));
                return;
            }
            directlyAdmittedEventId = eventId;
        }

        MapResult result = presenter.loadEventMap(eventId);
        if (!result.success()) {
            UiMessages.error(result.message());
            return;
        }

        openTicketDialog(eventId, result.eventMap());
    }

    private void loadEventById(UUID eventId) {
        directlyAdmittedEventId = eventId;
        MapResult result = presenter.loadEventMap(eventId);
        if (!result.success()) {
            UiMessages.error(result.message());
            return;
        }
        openTicketDialog(eventId, result.eventMap());
    }

    // ── Ticket-selection dialog (opened after queue admission) ──────

    private void openTicketDialog(UUID eventId, EventMapDTO eventMap) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(eventMap.eventName() + " — Ticket Selection");
        dialog.setWidth("min(900px, 90vw)");
        dialog.setMaxHeight("85vh");
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(false);
        dialog.setDraggable(true);
        dialog.setResizable(true);

        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);
        content.getStyle().set("overflow-y", "auto");

        Span orderStatus = new Span();
        Span resStatus = new Span("Select tickets below to add to your order.");
        resStatus.getStyle().set("white-space", "pre-line");
        refreshDialogOrderStatus(orderStatus);

        renderTicketDialogContent(content, eventId, eventMap, orderStatus, resStatus);

        dialog.add(content);

        Button closeButton = new Button("Close", e -> dialog.close());
        dialog.getFooter().add(closeButton);

        dialog.addOpenedChangeListener(e -> {
            if (!e.isOpened()) {
                releaseQueueSlot();
                refreshActiveOrderStatus();
            }
        });

        dialog.open();
    }

    private void renderTicketDialogContent(VerticalLayout content, UUID eventId,
            EventMapDTO eventMap, Span orderStatus, Span resStatus) {
        content.removeAll();

        content.add(
                new Span("Company: " + eventMap.companyName()),
                new Span("Status: " + eventMap.status().name()));

        if (eventMap.description() != null && !eventMap.description().isBlank()) {
            content.add(new Paragraph(eventMap.description()));
        }

        if (!eventMap.purchaseRestrictions().isEmpty() || !eventMap.visibleDiscounts().isEmpty()) {
            content.add(new PolicyBadgesPanel(eventMap.purchaseRestrictions(), eventMap.visibleDiscounts()));
        }

        content.add(orderStatus, resStatus);

        if (eventMap.zones().isEmpty()) {
            content.add(new Paragraph("No inventory zones available for this event."));
            return;
        }

        for (EventMapDTO.ZoneInfo zone : eventMap.zones()) {
            content.add(dialogZoneDetails(zone, eventId, content, orderStatus, resStatus));
        }

        if (eventMap.layout() != null && !eventMap.layout().cells().isEmpty()) {
            content.add(renderLayoutGrid(eventMap.layout()));
        }
    }

    private Details dialogZoneDetails(EventMapDTO.ZoneInfo zone, UUID eventId,
            VerticalLayout dialogContent, Span orderStatus, Span resStatus) {
        VerticalLayout zoneContent = new VerticalLayout();
        zoneContent.setPadding(false);
        zoneContent.setSpacing(false);
        zoneContent.add(
                new Span("Type: " + zone.type().name()),
                new Span("Price: " + formatPrice(zone.pricePerTicket())));

        boolean canReserve = !ordersPresenter.currentSessionState().noSession();

        if (zone.type() == ZoneType.GENERAL_ADMISSION) {
            IntegerField quantity = new IntegerField("Quantity");
            quantity.setMin(1);
            quantity.setValue(1);
            Button addGA = new Button("Add GA tickets", event -> {
                int qty = quantity.getValue() == null ? 0 : quantity.getValue();
                OrderMutationResult result = ordersPresenter.addGATickets(eventId, zone.id(), qty);
                resStatus.setText(result.message());
                if (!result.success()) {
                    UiMessages.error(result.message());
                    return;
                }
                UiMessages.success(result.message());
                refreshDialogOrderStatus(orderStatus);
                MapResult mapResult = presenter.loadEventMap(eventId);
                if (mapResult.success()) {
                    renderTicketDialogContent(dialogContent, eventId, mapResult.eventMap(),
                            orderStatus, resStatus);
                }
            });
            addGA.setEnabled(canReserve);
            zoneContent.add(
                    new Span("Capacity: " + zone.maxCapacity()),
                    new Span("Available: " + zone.availableCount()),
                    new Span("Sold: " + zone.soldCount()),
                    new HorizontalLayout(quantity, addGA));
        } else {
            zoneContent.add(new Span("Seats: " + zone.seats().size()));
            zoneContent.add(dialogSeatMap(zone, eventId, resStatus, orderStatus));
        }

        Details details = new Details(zone.name(), zoneContent);
        details.setOpened(true);
        return details;
    }

    private Component dialogSeatMap(EventMapDTO.ZoneInfo zone, UUID eventId,
            Span resStatus, Span orderStatus) {
        SeatMapComponent map = new SeatMapComponent(orderSeats(zone.seats()));
        Button addSelected = new Button("Add selected seats");
        addSelected.setEnabled(false);
        Span stagingHint = new Span(
                "Click available seats to select them, click again to deselect, then add to cart.");

        boolean canReserve = !ordersPresenter.currentSessionState().noSession();
        map.setSelectionCountListener(count -> {
            addSelected.setEnabled(canReserve && count > 0);
            addSelected.setText(count == 0
                    ? "Add selected seats"
                    : "Add selected seats (" + count + ")");
        });
        map.setCommitListener(seatIds -> {
            OrderMutationResult result = ordersPresenter.addAssignedSeats(eventId, zone.id(), seatIds);
            resStatus.setText(result.message());
            if (!result.success()) {
                UiMessages.error(result.message());
            } else {
                UiMessages.success(result.message());
                refreshDialogOrderStatus(orderStatus);
                map.markSeatsTaken(seatIds);
            }
        });
        addSelected.addClickListener(event -> map.requestAddSelection());

        map.setEnabled(canReserve);
        if (!canReserve) {
            map.getStyle().set("opacity", "0.5").set("pointer-events", "none");
            stagingHint.setText("Start a session to select seats.");
        }

        VerticalLayout box = new VerticalLayout(stagingHint, map, addSelected);
        box.setPadding(false);
        box.setSpacing(true);
        return box;
    }

    private void refreshDialogOrderStatus(Span target) {
        OrderResult result = ordersPresenter.loadCurrentOrder();
        if (!result.success()) {
            target.setText(result.message());
            return;
        }
        ActiveOrderDto order = result.order();
        if (order == null) {
            target.setText("No active order yet — adding tickets will start one.");
            return;
        }
        int ticketCount = order.getItems().stream().mapToInt(item -> item.getQuantity()).sum();
        String eventLabel = order.getEventName() != null && !order.getEventName().isBlank()
                ? order.getEventName()
                : order.getEventId().toString();
        target.setText("Active order: " + eventLabel
                + " | " + ticketCount + " ticket(s) | total " + formatPrice(order.getTotalPrice()));
    }

    private void releaseQueueSlot() {
        if (directlyAdmittedEventId != null) {
            queuePresenter.notifyLeft(directlyAdmittedEventId);
            directlyAdmittedEventId = null;
        }
    }

    private Component renderLayoutGrid(EventMapDTO.LayoutInfo layout) {
        Div gridBox = new Div();
        gridBox.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(" + layout.cols() + ", 26px)")
                .set("gap", "2px")
                .set("margin-top", "var(--lumo-space-s)");
        Map<Long, EventMapDTO.CellInfo> byPos = new HashMap<>();
        for (EventMapDTO.CellInfo cell : layout.cells()) {
            byPos.put((long) cell.row() * layout.cols() + cell.col(), cell);
        }
        for (int r = 0; r < layout.rows(); r++) {
            for (int c = 0; c < layout.cols(); c++) {
                EventMapDTO.CellInfo cell = byPos.get((long) r * layout.cols() + c);
                Div d = new Div();
                d.getStyle()
                        .set("width", "26px").set("height", "26px")
                        .set("border", "1px solid var(--lumo-contrast-20pct)")
                        .set("font-size", "10px").set("text-align", "center")
                        .set("line-height", "26px").set("color", "white");
                if (cell == null) {
                    d.getStyle().set("background", "var(--lumo-contrast-5pct)");
                } else {
                    d.setText(glyphFor(cell.type()));
                    d.getStyle().set("background", colorFor(cell.type()));
                    if (cell.label() != null) {
                        d.setTitle(cell.label());
                    }
                }
                gridBox.add(d);
            }
        }
        VerticalLayout box = new VerticalLayout(new H4("Hall layout"), gridBox);
        box.setPadding(false);
        return box;
    }

    private static String glyphFor(LayoutCellType type) {
        return switch (type) {
            case SEAT -> "S";
            case GENERAL_ADMISSION -> "G";
            case BLOCKED -> "X";
            case STAGE -> "ST";
            case OBJECT -> "O";
        };
    }

    private static String colorFor(LayoutCellType type) {
        return switch (type) {
            case SEAT -> "#1976d2";
            case GENERAL_ADMISSION -> "#2e7d32";
            case BLOCKED -> "#616161";
            case STAGE -> "#6a1b9a";
            case OBJECT -> "#ef6c00";
        };
    }

    /**
     * Orders seats by row label, then by seat number (numerically when parseable,
     * falling back to lexicographic order).
     */
    private List<EventMapDTO.SeatInfo> orderSeats(List<EventMapDTO.SeatInfo> seats) {
        return seats.stream()
                .sorted(Comparator.comparing(EventMapDTO.SeatInfo::row)
                        .thenComparingInt(seat -> seatNumberOrder(seat.seatNumber()))
                        .thenComparing(EventMapDTO.SeatInfo::seatNumber))
                .toList();
    }

    private int seatNumberOrder(String seatNumber) {
        try {
            return Integer.parseInt(seatNumber.trim());
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private void refreshSessionStatus() {
        sessionStatus.setText(ordersPresenter.currentSessionLabel());
    }

    private void refreshActiveOrderStatus() {
        OrderResult result = ordersPresenter.loadCurrentOrder();
        if (!result.success()) {
            activeOrderStatus.setText(result.message());
            return;
        }
        ActiveOrderDto order = result.order();
        if (order == null) {
            activeOrderStatus
                    .setText("No active order yet. Adding tickets here will start one for the selected event.");
            return;
        }
        int ticketCount = order.getItems().stream().mapToInt(item -> item.getQuantity()).sum();
        String eventLabel = order.getEventName() != null && !order.getEventName().isBlank()
                ? order.getEventName()
                : order.getEventId().toString();
        activeOrderStatus.setText("Active order for " + eventLabel
                + " | " + ticketCount + " ticket(s) | total " + formatPrice(order.getTotalPrice())
                + " — manage on Orders page");
    }

    private String formatCategory(EventCategory category) {
        return category == null ? "" : category.name().replace('_', ' ');
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "" : DATE_TIME_FORMATTER.format(instant);
    }

    private String formatPrice(BigDecimal price) {
        return price == null ? "N/A" : price.toPlainString();
    }
}
