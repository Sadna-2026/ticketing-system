package com.ticketing.presentation.vaadin.views;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.MapResult;
import com.ticketing.presentation.vaadin.presenters.EventsPresenter.SearchResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
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

    private final TextField text = new TextField("Search text");
    private final TextField region = new TextField("Region");
    private final TextField companyName = new TextField("Company");
    private final ComboBox<EventCategory> category = new ComboBox<>("Category");
    private final BigDecimalField minPrice = new BigDecimalField("Min price");
    private final BigDecimalField maxPrice = new BigDecimalField("Max price");
    private final DatePicker fromDate = new DatePicker("From date");
    private final DatePicker toDate = new DatePicker("To date");
    private final Span resultsStatus = new Span("Search for events to see results.");
    private final Grid<EventSummaryDTO> resultsGrid = new Grid<>(EventSummaryDTO.class, false);
    private final Button viewMap = new Button("View selected map");
    private final VerticalLayout mapDisplay = new VerticalLayout();

    private EventSummaryDTO selectedEvent;

    public EventsView(EventsPresenter presenter) {
        this.presenter = presenter;

        setPadding(true);
        setSpacing(true);
        setMaxWidth("1100px");
        getStyle().set("margin", "0 auto");

        configureFields();
        configureResultsGrid();
        configureMapDisplay();

        add(
                new H2("Events"),
                new Paragraph("Search published events and inspect venue map inventory before choosing tickets."),
                searchSection(),
                new H3("Search results"),
                resultsStatus,
                resultsGrid,
                viewMap,
                new H3("Event map and inventory"),
                mapDisplay
        );
    }

    private void configureFields() {
        category.setItems(EventCategory.values());
        category.setItemLabelGenerator(this::formatCategory);

        text.setPlaceholder("Event, artist, or description");
        region.setPlaceholder("Exact region");
        companyName.setPlaceholder("Company name");

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
        mapDisplay.add(new Paragraph("Select an event from the results to view its venue map and inventory."));
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
                companyName.getValue(),
                minPrice.getValue(),
                maxPrice.getValue(),
                fromDate.getValue(),
                toDate.getValue()
        );

        selectedEvent = null;
        viewMap.setEnabled(false);
        mapDisplay.removeAll();
        mapDisplay.add(new Paragraph("Select an event from the results to view its venue map and inventory."));

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
        mapDisplay.removeAll();
        mapDisplay.add(new Paragraph("Select an event from the results to view its venue map and inventory."));
    }

    private void loadSelectedEventMap() {
        UUID eventId = selectedEvent == null ? null : selectedEvent.id();
        MapResult result = presenter.loadEventMap(eventId);

        mapDisplay.removeAll();
        if (!result.success()) {
            mapDisplay.add(new Paragraph(result.message()));
            UiMessages.error(result.message());
            return;
        }

        renderEventMap(result.eventMap());
        UiMessages.success(result.message());
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

        if (zone.maxCapacity() != null) {
            content.add(
                    new Span("Capacity: " + zone.maxCapacity()),
                    new Span("Available: " + zone.availableCount()),
                    new Span("Sold: " + zone.soldCount())
            );
        } else {
            content.add(new Span("Seats: " + zone.seats().size()));
            HorizontalLayout seats = new HorizontalLayout();
            seats.setSpacing(true);
            seats.getStyle().set("flex-wrap", "wrap");
            for (EventMapDTO.SeatInfo seat : zone.seats()) {
                Span badge = new Span(seat.row() + "-" + seat.seatNumber() + " "
                        + (seat.available() ? "available" : "unavailable"));
                badge.getStyle()
                        .set("border", "1px solid var(--lumo-contrast-20pct)")
                        .set("border-radius", "999px")
                        .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                        .set("background", seat.available()
                                ? "var(--lumo-success-color-10pct)"
                                : "var(--lumo-error-color-10pct)");
                seats.add(badge);
            }
            content.add(seats);
        }

        Details details = new Details(zone.name(), content);
        details.setOpened(true);
        return details;
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
