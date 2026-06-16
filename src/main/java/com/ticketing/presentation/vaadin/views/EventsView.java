package com.ticketing.presentation.vaadin.views;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.ticketing.presentation.vaadin.presenters.EventsPresenter;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.MapResult;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.SearchResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderMutationResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

@Route(value = "events", layout = MainLayout.class)
@PageTitle("Events")
@SpringComponent
@UIScope
public class EventsView extends VerticalLayout {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final EventsPresenter presenter;
    private final OrdersPresenter ordersPresenter;

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
    private final Button viewMap = new Button("View selected map");
    private final Span mapStatus = new Span("Select an event from the results to load its map.");
    private final Span reservationStatus = new Span("Add tickets to your active order from the map below.");
    private final VerticalLayout mapDisplay = new VerticalLayout();

    private static final int MAP_POLL_INTERVAL_MS = 10_000;

    private static final String[] ZONE_COLORS = {
            "#1976d2", "#2e7d32", "#d32f2f", "#f57c00", "#7b1fa2",
            "#00838f", "#c2185b", "#6d4c41", "#1565c0", "#558b2f", "#ad1457", "#455a64"
    };
    private static final String SELECTED_COLOR = "#ffb300";

    private EventSummaryDTO selectedEvent;
    private EventMapDTO currentEventMap;
    private Registration mapPollRegistration;

    // Interactive-map selection state (assigned seats staged before checkout).
    private final Set<UUID> selectedSeatIds = new LinkedHashSet<>();
    private final Map<UUID, Button> seatCellButtons = new HashMap<>();
    private final Map<UUID, String> seatBaseColor = new HashMap<>();
    private final Map<UUID, UUID> seatZoneId = new HashMap<>();
    private Span selectionSummary;
    private Button addSelectedButton;

    public EventsView(EventsPresenter presenter, OrdersPresenter ordersPresenter) {
        this.presenter = presenter;
        this.ordersPresenter = ordersPresenter;

        setPadding(true);
        setSpacing(true);
        setMaxWidth("1100px");
        getStyle().set("margin", "0 auto");

        configureFields();
        configureResultsGrid();
        configureMapDisplay();

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
                viewMap,
                new H3("Event map and ticket selection"),
                mapStatus,
                reservationStatus,
                mapDisplay);
        refreshSessionStatus();
        refreshActiveOrderStatus();
        addAttachListener(event -> {
            refreshSessionStatus();
            refreshActiveOrderStatus();
            if (currentEventMap != null) {
                startMapPolling();
            }
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        stopMapPolling();
        super.onDetach(detachEvent);
    }

    private void configureFields() {
        reservationStatus.getStyle().set("white-space", "pre-line");
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

    private void configureMapDisplay() {
        mapDisplay.setPadding(false);
        mapDisplay.setSpacing(true);
        mapDisplay.add(new Paragraph("Select an event from the results, then load its map to add tickets."));
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
        currentEventMap = null;
        clearSelectionState();
        stopMapPolling();
        viewMap.setEnabled(false);
        resetMapDisplay();

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
        currentEventMap = null;
        clearSelectionState();
        stopMapPolling();
        viewMap.setEnabled(false);
        resultsStatus.setText("Search for events to see results.");
        resetMapDisplay();
        UiMessages.info("Filters cleared.");
    }

    private void loadSelectedEventMap() {
        loadSelectedEventMap(true);
    }

    private void loadSelectedEventMap(boolean notify) {
        UUID eventId = selectedEvent == null ? null : selectedEvent.id();
        MapResult result = presenter.loadEventMap(eventId);

        mapDisplay.removeAll();
        clearSelectionState();
        stopMapPolling();
        currentEventMap = result.success() ? result.eventMap() : null;
        mapStatus.setText(result.message());
        if (!result.success()) {
            mapDisplay.add(new Paragraph(result.message()));
            UiMessages.error(result.message());
            return;
        }

        renderEventMap(result.eventMap());
        startMapPolling();
        if (notify) {
            UiMessages.success(result.message());
        }
    }

    private void addGATickets(UUID zoneId, Integer quantity) {
        UUID eventId = currentEventId();
        if (eventId == null) {
            return;
        }
        OrderMutationResult result = ordersPresenter.addGATickets(eventId, zoneId, quantity == null ? 0 : quantity);
        handleReservationResult(result);
        if (result.success()) { // refresh inventory counts after adding GA tickets
            loadSelectedEventMap(false);
        }
    }

    private void handleReservationResult(OrderMutationResult result) {
        reservationStatus.setText(result.message());
        if (!result.success()) {
            UiMessages.error(result.message());
            return;
        }
        UiMessages.success(result.message());
        refreshActiveOrderStatus();
    }

    private UUID currentEventId() {
        if (currentEventMap != null) {
            return currentEventMap.eventId();
        }
        if (selectedEvent != null) {
            return selectedEvent.id();
        }
        reservationStatus.setText("Load an event map before adding tickets.");
        UiMessages.error("Load an event map before adding tickets.");
        return null;
    }

    private void resetMapDisplay() {
        clearSelectionState();
        stopMapPolling();
        mapStatus.setText("Select an event from the results to load its map.");
        mapDisplay.removeAll();
        mapDisplay.add(new Paragraph("Select an event from the results, then load its map to add tickets."));
    }

    private void startMapPolling() {
        getUI().ifPresent(ui -> {
            ui.setPollInterval(MAP_POLL_INTERVAL_MS);
            if (mapPollRegistration == null) {
                mapPollRegistration = ui.addPollListener(event -> {
                    // Refresh availability while the buyer isn't mid-selection (avoids wiping picks).
                    if (currentEventMap != null && selectedSeatIds.isEmpty()) {
                        loadSelectedEventMap(false);
                    }
                });
            }
        });
    }

    private void stopMapPolling() {
        if (mapPollRegistration != null) {
            mapPollRegistration.remove();
            mapPollRegistration = null;
        }
        getUI().ifPresent(ui -> ui.setPollInterval(-1));
    }

    private void renderEventMap(EventMapDTO eventMap) {
        mapDisplay.add(
                new H4(eventMap.eventName()),
                new Span("Company: " + eventMap.companyName()),
                new Span("Status: " + eventMap.status().name()));

        if (eventMap.description() != null && !eventMap.description().isBlank()) {
            mapDisplay.add(new Paragraph(eventMap.description()));
        }
        if (!eventMap.purchaseRestrictions().isEmpty() || !eventMap.visibleDiscounts().isEmpty()) {
            mapDisplay.add(new PolicyBadgesPanel(eventMap.purchaseRestrictions(), eventMap.visibleDiscounts()));
        }
        if (eventMap.zones().isEmpty()) {
            mapDisplay.add(new Paragraph("No tickets are available for this event."));
            return;
        }
        if (eventMap.layout() == null || eventMap.layout().cells().isEmpty()) {
            mapDisplay.add(new Paragraph("This event has no seating map yet."));
            return;
        }
        mapDisplay.add(renderInteractiveMap(eventMap));
    }

    /**
     * One interactive venue map: every zone gets its own colour, assigned seats toggle into the
     * selection (amber) and general-admission areas open a quantity popup. The buyable inventory
     * and this map come from the same layout cells, so what you see is what you can buy.
     */
    private Component renderInteractiveMap(EventMapDTO eventMap) {
        clearSelectionState();

        Map<UUID, String> zoneColor = new HashMap<>();
        Map<UUID, EventMapDTO.ZoneInfo> zoneById = new HashMap<>();
        int ci = 0;
        for (EventMapDTO.ZoneInfo zone : eventMap.zones()) {
            zoneById.put(zone.id(), zone);
            zoneColor.put(zone.id(), ZONE_COLORS[ci++ % ZONE_COLORS.length]);
        }
        Map<UUID, EventMapDTO.SeatInfo> seatById = new HashMap<>();
        for (EventMapDTO.ZoneInfo zone : eventMap.zones()) {
            for (EventMapDTO.SeatInfo seat : zone.seats()) {
                seatById.put(seat.id(), seat);
                seatZoneId.put(seat.id(), zone.id());
            }
        }

        boolean canReserve = !ordersPresenter.currentSessionState().noSession();
        EventMapDTO.LayoutInfo layout = eventMap.layout();
        Div gridBox = new Div();
        gridBox.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(" + layout.cols() + ", 28px)")
                .set("gap", "2px").set("margin-top", "var(--lumo-space-s)");
        Map<Long, EventMapDTO.CellInfo> byPos = new HashMap<>();
        for (EventMapDTO.CellInfo cell : layout.cells()) {
            byPos.put((long) cell.row() * layout.cols() + cell.col(), cell);
        }
        for (int r = 0; r < layout.rows(); r++) {
            for (int c = 0; c < layout.cols(); c++) {
                gridBox.add(buildMapCell(byPos.get((long) r * layout.cols() + c),
                        zoneById, zoneColor, seatById, canReserve));
            }
        }

        selectionSummary = new Span("No seats selected.");
        addSelectedButton = new Button("Add selected seats to cart", e -> addSelectedSeats());
        addSelectedButton.setEnabled(false);

        VerticalLayout box = new VerticalLayout(
                new H4("Choose your tickets"),
                buildLegend(eventMap, zoneColor),
                gridBox,
                selectionSummary,
                addSelectedButton);
        box.setPadding(false);
        if (!canReserve) {
            box.add(new Span("Start a guest or member session to select tickets."));
        }
        return box;
    }

    private Component buildMapCell(EventMapDTO.CellInfo cell,
            Map<UUID, EventMapDTO.ZoneInfo> zoneById, Map<UUID, String> zoneColor,
            Map<UUID, EventMapDTO.SeatInfo> seatById, boolean canReserve) {
        Button d = new Button();
        d.getStyle().set("min-width", "28px").set("width", "28px").set("height", "28px")
                .set("padding", "0").set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("font-size", "10px").set("color", "white").set("background", "var(--lumo-contrast-5pct)");
        if (cell == null) {
            d.setEnabled(false);
            d.getStyle().set("color", "inherit");
            return d;
        }
        switch (cell.type()) {
            case SEAT -> {
                EventMapDTO.SeatInfo seat = cell.seatId() == null ? null : seatById.get(cell.seatId());
                String color = zoneColor.getOrDefault(cell.zoneId(), "#1976d2");
                if (seat == null || !seat.available()) {
                    d.setText("×");
                    d.getStyle().set("background", "#9e9e9e");
                    d.setEnabled(false);
                } else {
                    d.setText("S");
                    d.getStyle().set("background", color);
                    d.getElement().setAttribute("title", "Seat " + seat.row() + seat.seatNumber());
                    d.setEnabled(canReserve);
                    if (canReserve) {
                        UUID seatId = seat.id();
                        seatCellButtons.put(seatId, d);
                        seatBaseColor.put(seatId, color);
                        d.addClickListener(e -> toggleSeat(seatId));
                    }
                }
            }
            case GENERAL_ADMISSION -> {
                EventMapDTO.ZoneInfo zone = zoneById.get(cell.zoneId());
                d.setText("G");
                d.getStyle().set("background", zoneColor.getOrDefault(cell.zoneId(), "#2e7d32"));
                if (zone != null) {
                    d.getElement().setAttribute("title", zone.name() + " — " + formatPrice(zone.pricePerTicket())
                            + " (avail " + zone.availableCount() + ")");
                    boolean sellable = canReserve && zone.availableCount() > 0;
                    d.setEnabled(sellable);
                    if (sellable) {
                        d.addClickListener(e -> openGaQuantityDialog(zone));
                    }
                }
            }
            default -> {
                d.setText(glyphFor(cell.type()));
                d.getStyle().set("background", colorFor(cell.type()));
                d.setEnabled(false);
                if (cell.label() != null) {
                    d.getElement().setAttribute("title", cell.label());
                }
            }
        }
        return d;
    }

    private Component buildLegend(EventMapDTO eventMap, Map<UUID, String> zoneColor) {
        VerticalLayout legend = new VerticalLayout();
        legend.setPadding(false);
        legend.setSpacing(false);
        for (EventMapDTO.ZoneInfo zone : eventMap.zones()) {
            Div swatch = new Div();
            swatch.getStyle().set("width", "14px").set("height", "14px").set("flex-shrink", "0")
                    .set("background", zoneColor.get(zone.id())).set("border-radius", "3px");
            boolean ga = zone.type() == ZoneType.GENERAL_ADMISSION;
            String avail = ga
                    ? "avail " + zone.availableCount() + "/" + zone.maxCapacity()
                    : zone.seats().stream().filter(EventMapDTO.SeatInfo::available).count() + " free";
            Span label = new Span(zone.name() + " (" + (ga ? "GA" : "Seats") + ") — "
                    + formatPrice(zone.pricePerTicket()) + " · " + avail);
            label.getStyle().set("font-size", "var(--lumo-font-size-s)");
            HorizontalLayout row = new HorizontalLayout(swatch, label);
            row.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
            legend.add(row);
        }
        return legend;
    }

    // Package-private for view-layer tests (a detached view can't drive overlay dialogs/clicks).
    void toggleSeat(UUID seatId) {
        Button btn = seatCellButtons.get(seatId);
        if (btn == null) {
            return;
        }
        if (selectedSeatIds.remove(seatId)) {
            btn.getStyle().set("background", seatBaseColor.getOrDefault(seatId, "#1976d2"));
        } else {
            selectedSeatIds.add(seatId);
            btn.getStyle().set("background", SELECTED_COLOR);
        }
        int n = selectedSeatIds.size();
        selectionSummary.setText(n == 0 ? "No seats selected." : n + " seat(s) selected.");
        addSelectedButton.setEnabled(n > 0);
        addSelectedButton.setText(n == 0
                ? "Add selected seats to cart"
                : "Add selected seats to cart (" + n + ")");
    }

    void addSelectedSeats() {
        UUID eventId = currentEventId();
        if (eventId == null || selectedSeatIds.isEmpty()) {
            return;
        }
        Map<UUID, List<UUID>> byZone = new HashMap<>();
        for (UUID seatId : selectedSeatIds) {
            UUID zoneId = seatZoneId.get(seatId);
            if (zoneId != null) {
                byZone.computeIfAbsent(zoneId, k -> new ArrayList<>()).add(seatId);
            }
        }
        boolean allOk = true;
        for (Map.Entry<UUID, List<UUID>> entry : byZone.entrySet()) {
            OrderMutationResult result = ordersPresenter.addAssignedSeats(eventId, entry.getKey(), entry.getValue());
            handleReservationResult(result);
            allOk = allOk && result.success();
        }
        if (allOk) {
            loadSelectedEventMap(false);
        }
    }

    private void openGaQuantityDialog(EventMapDTO.ZoneInfo zone) {
        buildGaQuantityDialog(zone).open();
    }

    /** Builds the GA quantity popup. Package-private so view-layer tests can drive it. */
    Dialog buildGaQuantityDialog(EventMapDTO.ZoneInfo zone) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Add tickets — " + zone.name());
        IntegerField quantity = new IntegerField("Quantity");
        quantity.setMin(1);
        quantity.setMax(zone.availableCount());
        quantity.setValue(1);
        Button add = new Button("Add to cart", e -> {
            Integer qty = quantity.getValue();
            dialog.close();
            addGATickets(zone.id(), qty);
        });
        add.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", e -> dialog.close());
        dialog.add(new VerticalLayout(
                new Span("Price: " + formatPrice(zone.pricePerTicket())),
                new Span("Available: " + zone.availableCount()),
                quantity,
                new HorizontalLayout(add, cancel)));
        return dialog;
    }

    private void clearSelectionState() {
        selectedSeatIds.clear();
        seatCellButtons.clear();
        seatBaseColor.clear();
        seatZoneId.clear();
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
