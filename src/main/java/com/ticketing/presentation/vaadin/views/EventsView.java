package com.ticketing.presentation.vaadin.views;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.dto.CompanySummaryDTO;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.MapResult;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.SearchResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderMutationResult;
import com.ticketing.presentation.vaadin.presenters.OrdersPresenter.OrderResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
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
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "events", layout = MainLayout.class)
@PageTitle("Events")
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

    private EventSummaryDTO selectedEvent;
    private EventMapDTO currentEventMap;

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
                mapDisplay
        );
        refreshSessionStatus();
        refreshActiveOrderStatus();
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
        resultsGrid.addColumn(EventSummaryDTO::name).setHeader("Event").setAutoWidth(true);
        resultsGrid.addColumn(event -> formatCategory(event.category())).setHeader("Category").setAutoWidth(true);
        resultsGrid.addColumn(event -> formatInstant(event.schedule().getStartTime())).setHeader("Starts").setAutoWidth(true);
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
                toDate
        );
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("640px", 2),
                new FormLayout.ResponsiveStep("960px", 4)
        );

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
                toDate.getValue()
        );

        selectedEvent = null;
        currentEventMap = null;
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
        viewMap.setEnabled(false);
        resultsStatus.setText("Search for events to see results.");
        resetMapDisplay();
    }

    private void loadSelectedEventMap() {
        UUID eventId = selectedEvent == null ? null : selectedEvent.id();
        MapResult result = presenter.loadEventMap(eventId);

        mapDisplay.removeAll();
        currentEventMap = result.success() ? result.eventMap() : null;
        mapStatus.setText(result.message());
        if (!result.success()) {
            mapDisplay.add(new Paragraph(result.message()));
            UiMessages.error(result.message());
            return;
        }

        renderEventMap(result.eventMap());
        UiMessages.success(result.message());
    }

    private void addGATickets(UUID zoneId, Integer quantity) {
        UUID eventId = currentEventId();
        if (eventId == null) {
            return;
        }
        OrderMutationResult result = ordersPresenter.addGATickets(eventId, zoneId, quantity == null ? 0 : quantity);
        handleReservationResult(result);
    }

    private void addAssignedSeat(UUID zoneId, UUID seatId) {
        UUID eventId = currentEventId();
        if (eventId == null) {
            return;
        }
        OrderMutationResult result = ordersPresenter.addAssignedSeat(eventId, zoneId, seatId);
        handleReservationResult(result);
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
        mapStatus.setText("Select an event from the results to load its map.");
        mapDisplay.removeAll();
        mapDisplay.add(new Paragraph("Select an event from the results, then load its map to add tickets."));
    }

    private void renderEventMap(EventMapDTO eventMap) {
        mapDisplay.add(
                new H4(eventMap.eventName()),
                new Span("Company: " + eventMap.companyName()),
                new Span("Status: " + eventMap.status().name())
        );

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
            activeOrderStatus.setText("No active order yet. Adding tickets here will start one for the selected event.");
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
