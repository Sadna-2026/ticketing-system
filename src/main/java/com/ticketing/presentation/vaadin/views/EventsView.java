package com.ticketing.presentation.vaadin.views;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
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
    private final Div eventCards = new Div();
    private final Span mapStatus = new Span("Select an event from the results to load its map.");
    private final Span reservationStatus = new Span("Add tickets to your active order from the map below.");
    private final VerticalLayout mapDisplay = new VerticalLayout();

    private static final int MAP_POLL_INTERVAL_MS = 10_000;

    private EventSummaryDTO selectedEvent;
    private EventMapDTO currentEventMap;
    private final Map<UUID, SeatMapComponent> zoneSeatMaps = new HashMap<>();
    private Registration mapPollRegistration;

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
                eventCards,
                new H3("Event map and ticket selection"),
                mapStatus,
                reservationStatus,
                mapDisplay);
        refreshSessionStatus();
        refreshActiveOrderStatus();
        addAttachListener(event -> {
            refreshSessionStatus();
            refreshActiveOrderStatus();
            if (currentEventMap != null && !zoneSeatMaps.isEmpty()) {
                refreshSeatMapsFromServer(false);
                startMapPolling();
            } else if (currentEventMap != null) {
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

    }

    private void configureResultsGrid() {
        eventCards.addClassName("app-event-grid");
    }

    /** Renders the search results as event cards (replaces the stock Grid). */
    private void renderEventCards(List<EventSummaryDTO> events) {
        eventCards.removeAll();
        if (events == null || events.isEmpty()) {
            return;
        }
        for (EventSummaryDTO event : events) {
            eventCards.add(eventCard(event));
        }
    }

    private Div eventCard(EventSummaryDTO event) {
        Div card = new Div();
        card.addClassName("app-event-card");

        Span cat = new Span(formatCategory(event.category()));
        cat.addClassName("app-event-cat");

        H3 title = new H3(event.name());

        Span when = new Span(formatInstant(event.schedule().getStartTime()));
        when.addClassName("app-event-meta");

        String statusName = event.status().name();
        Span status = new Span(statusName);
        status.addClassNames("app-badge",
                "PUBLISHED".equals(statusName) ? "app-badge--success" : "app-badge--neutral");

        Button view = new Button("View seats", e -> {
            selectedEvent = event;
            loadSelectedEventMap();
        });
        view.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Div foot = new Div(status, view);
        foot.addClassName("app-event-foot");

        card.add(cat, title, when, foot);
        return card;
    }

    private void configureMapDisplay() {
        mapDisplay.setPadding(false);
        mapDisplay.setSpacing(true);
        mapDisplay.add(new Paragraph("Select an event from the results, then load its map to add tickets."));
    }

    private VerticalLayout searchSection() {
        Button search = new Button("Search events", event -> searchEvents());
        search.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
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
        zoneSeatMaps.clear();
        stopMapPolling();
        resetMapDisplay();

        if (!result.success()) {
            renderEventCards(List.of());
            resultsStatus.setText(result.message());
            UiMessages.error(result.message());
            return;
        }

        renderEventCards(result.events());
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
        renderEventCards(List.of());
        selectedEvent = null;
        currentEventMap = null;
        zoneSeatMaps.clear();
        stopMapPolling();
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
        zoneSeatMaps.clear();
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

    private void addAssignedSeats(UUID zoneId, List<UUID> seatIds, SeatMapComponent map) {
        UUID eventId = currentEventId();
        if (eventId == null) {
            return;
        }
        OrderMutationResult result = ordersPresenter.addAssignedSeats(eventId, zoneId, seatIds);
        handleReservationResult(result);
        if (result.success()) {
            map.markSeatsTaken(seatIds);
        } else {
            refreshSeatMapsFromServer(true);
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
        zoneSeatMaps.clear();
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
                    if (currentEventMap != null && !zoneSeatMaps.isEmpty()) {
                        refreshSeatMapsFromServer(false);
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

    /**
     * Reload seat availability from the server and reconcile staged picks on every open map.
     * When {@code notifyLost} is true (typically after a failed add), tell the buyer which
     * seats were dropped from their selection because another cart took them.
     */
    private void refreshSeatMapsFromServer(boolean notifyLost) {
        if (selectedEvent == null || zoneSeatMaps.isEmpty()) {
            return;
        }
        MapResult result = presenter.loadEventMap(selectedEvent.id());
        if (!result.success() || result.eventMap() == null) {
            return;
        }
        currentEventMap = result.eventMap();

        List<EventMapDTO.ZoneInfo> assignedZones = currentEventMap.zones().stream()
                .filter(zone -> zone.type() == ZoneType.ASSIGNED_SEATING && zoneSeatMaps.containsKey(zone.id()))
                .toList();
        if (assignedZones.isEmpty()) {
            return;
        }

        List<String> allLost = new ArrayList<>();
        AtomicInteger pending = new AtomicInteger(assignedZones.size());
        for (EventMapDTO.ZoneInfo zone : assignedZones) {
            SeatMapComponent map = zoneSeatMaps.get(zone.id());
            map.syncAvailability(orderSeats(zone.seats()), lost -> {
                allLost.addAll(lost);
                if (pending.decrementAndGet() == 0) {
                    notifyLostSeats(allLost, notifyLost);
                }
            });
        }
    }

    private void notifyLostSeats(List<String> lost, boolean prominent) {
        if (lost.isEmpty()) {
            return;
        }
        String labels = String.join(", ", lost);
        if (prominent) {
            String extra = " Seat map refreshed. These seats are no longer available and were removed from your selection: "
                    + labels;
            reservationStatus.setText(reservationStatus.getText() + extra);
            UiMessages.info(extra.trim());
        } else {
            String message = "Seat availability changed. Removed from your selection: " + labels;
            reservationStatus.setText(message);
            UiMessages.info(message);
        }
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

        if (eventMap.venueMap().isEmpty()) {
            mapDisplay.add(new Paragraph("No venue sections are available for this event."));
        } else {
            VerticalLayout sections = new VerticalLayout(new H4("Venue sections"));
            sections.setPadding(false);
            for (Map.Entry<String, UUID> entry : eventMap.venueMap().entrySet()) {
                sections.add(new Span(entry.getKey() + " -> zone " + entry.getValue()));
            }
            mapDisplay.add(sections);
        }

        if (eventMap.zones().isEmpty()) {
            mapDisplay.add(new Paragraph("No inventory zones are available for this event."));
            return;
        }

        VerticalLayout zones = new VerticalLayout(new H4("Inventory zones"));
        zones.setPadding(false);
        for (EventMapDTO.ZoneInfo zone : eventMap.zones()) {
            zones.add(zoneDetails(zone));
        }
        mapDisplay.add(zones);

        if (eventMap.layout() != null && !eventMap.layout().cells().isEmpty()) {
            mapDisplay.add(renderLayoutGrid(eventMap.layout()));
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

    private Details zoneDetails(EventMapDTO.ZoneInfo zone) {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(false);
        content.add(
                new Span("Type: " + zone.type().name()),
                new Span("Price: " + formatPrice(zone.pricePerTicket())));

        if (zone.type() == ZoneType.GENERAL_ADMISSION) {
            IntegerField quantity = new IntegerField("Quantity");
            quantity.setMin(1);
            quantity.setValue(1);
            Button addGA = new Button("Add GA tickets", event -> addGATickets(zone.id(), quantity.getValue()));
            addGA.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            boolean canReserve = !ordersPresenter.currentSessionState().noSession();
            addGA.setEnabled(canReserve);
            content.add(
                    new Span("Capacity: " + zone.maxCapacity()),
                    new Span("Available: " + zone.availableCount()),
                    new Span("Sold: " + zone.soldCount()),
                    new HorizontalLayout(quantity, addGA));
        } else {
            content.add(new Span("Seats: " + zone.seats().size()));
            content.add(seatMap(zone));
        }

        Details details = new Details(zone.name(), content);
        details.setOpened(true);
        return details;
    }

    /**
     * Assigned-seating zone: stage seats locally (green/yellow toggle), then commit in one
     * batch so purchase policies (min qty, no-orphan, etc.) evaluate the full pick.
     */
    private Component seatMap(EventMapDTO.ZoneInfo zone) {
        SeatMapComponent map = new SeatMapComponent(orderSeats(zone.seats()));
        Button addSelected = new Button("Add selected seats");
        addSelected.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addSelected.setEnabled(false);
        Span stagingHint = new Span("Click available seats to select them, click again to deselect, then add to cart.");

        boolean canReserve = !ordersPresenter.currentSessionState().noSession();
        map.setSelectionCountListener(count -> {
            addSelected.setEnabled(canReserve && count > 0);
            addSelected.setText(count == 0 ? "Add selected seats" : "Add selected seats (" + count + ")");
        });
        map.setCommitListener(seatIds -> addAssignedSeats(zone.id(), seatIds, map));
        addSelected.addClickListener(event -> map.requestAddSelection());

        map.setEnabled(canReserve);
        addSelected.setEnabled(false);
        zoneSeatMaps.put(zone.id(), map);
        if (!canReserve) {
            map.getStyle().set("opacity", "0.5");
            map.getStyle().set("pointer-events", "none");
            stagingHint.setText("Start a guest or member session to select seats.");
        }

        Div stage = new Div(new Span("STAGE"));
        stage.addClassName("app-stage");

        VerticalLayout box = new VerticalLayout(stagingHint, stage, map, addSelected);
        box.setPadding(false);
        box.setSpacing(true);
        return box;
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
