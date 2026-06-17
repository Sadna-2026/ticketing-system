package com.ticketing.presentation.vaadin.views;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.SaleMethod;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.presentation.vaadin.components.VenueLayoutEditorComponent;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventMapResult;
import com.ticketing.presentation.vaadin.util.DestructiveActionDialogs;
import com.ticketing.presentation.vaadin.util.RequiredFields;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Edit an existing event from one place: change its details, tune zone pricing and
 * GA capacity, repaint the visual hall layout (DRAFT events only — the layout is locked
 * once published), and publish or cancel the event.
 *
 * <p>All mutations go through {@link CompanyPresenter}, so authorization is still enforced
 * by the application layer; this dialog only surfaces the controls and their responses.
 */
public class EditEventDialog extends Dialog {



    private final CompanyPresenter presenter;
    private final String companyName;
    private UUID eventId;
    private EventStatus status;

    // Event selection
    private final ComboBox<EventSummaryDTO> eventPicker = new ComboBox<>("Event to manage");
    private final VerticalLayout body = new VerticalLayout();

    // Event details
    private static final class ZoneOption {
        final UUID id;
        final String name;
        final boolean ga;
        final BigDecimal price;
        final int gaCapacity;
        ZoneOption(UUID id, String name, boolean ga, BigDecimal price, int gaCapacity) {
            this.id = id;
            this.name = name;
            this.ga = ga;
            this.price = price;
            this.gaCapacity = gaCapacity;
        }
    }

    private final TextField eventName = new TextField("Event name");
    private final TextField description = new TextField("Description");
    private final TextField artist = new TextField("Artist");
    private final DateTimePicker startTime = new DateTimePicker("Start time");
    private final DateTimePicker endTime = new DateTimePicker("End time");
    private final DateTimePicker doorsOpenTime = new DateTimePicker("Doors open time");
    private final Span detailsStatus = new Span();

    // Zones
    private final List<ZoneOption> zones = new ArrayList<>();
    private final VerticalLayout zonesPanel = new VerticalLayout();
    private final Span zonesStatus = new Span();

    // Layout editor
    private final VenueLayoutEditorComponent layoutEditor = new VenueLayoutEditorComponent();
    private final Span layoutStatus = new Span();

    // Lottery window
    private final DateTimePicker lotteryOpenPicker = new DateTimePicker("Registration opens");
    private final DateTimePicker lotteryClosePicker = new DateTimePicker("Registration closes");
    private boolean isLotteryEvent;
    // Lifecycle
    private final Span lifecycleStatus = new Span();

    public EditEventDialog(CompanyPresenter presenter, String companyName) {
        this.presenter = presenter;
        this.companyName = companyName;

        setHeaderTitle("Edit event — " + companyName);
        setWidth("1100px");

        eventPicker.setItemLabelGenerator(EventSummaryDTO::name);
        eventPicker.setPlaceholder("Select an event");
        List<EventSummaryDTO> events = presenter.listCompanyEvents(companyName);
        eventPicker.setItems(events == null ? List.of() : events);
        eventPicker.addValueChangeListener(e -> loadSelectedEvent(e.getValue()));

        body.setPadding(false);
        body.setSpacing(true);
        showHint();

        VerticalLayout content = new VerticalLayout(eventPicker, body);
        content.setPadding(false);
        content.setSpacing(true);
        add(content);

        getFooter().add(new Button("Close", e -> close()));
    }

    private void showHint() {
        body.removeAll();
        body.add(new Span("Select an event to edit its details, zones, and layout."));
    }

    private void loadSelectedEvent(EventSummaryDTO summary) {
        if (summary == null) {
            eventId = null;
            showHint();
            return;
        }
        eventId = summary.id();
        status = summary.status();
        isLotteryEvent = summary.saleMethod() == SaleMethod.LOTTERY;
        prefillDetails(summary);
        loadEventMap();
        body.removeAll();
        body.add(
                buildDetailsSection(),
                buildZonesSection(),
                buildLayoutSection(),
                buildLifecycleSection());
    }

    // ── Event details ──

    private void prefillDetails(EventSummaryDTO event) {
        eventName.setValue(nullToEmpty(event.name()));
        artist.setPlaceholder("Leave blank to keep current");
        if (event.schedule() != null) {
            startTime.setValue(toLocal(event.schedule().getStartTime()));
            endTime.setValue(toLocal(event.schedule().getEndTime()));
            doorsOpenTime.setValue(toLocal(event.schedule().getDoorsOpenTime()));
        }
        RequiredFields.markRequired(eventName, "Event name is required.");
    }

    private VerticalLayout buildDetailsSection() {
        HorizontalLayout row1 = new HorizontalLayout(eventName, artist);
        row1.setWidthFull();
        HorizontalLayout row2 = new HorizontalLayout(startTime, endTime, doorsOpenTime);
        row2.setWidthFull();
        Button save = new Button("Save details", e -> saveDetails());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout section = new VerticalLayout(
                new H4("Event details"),
                row1, description, row2,
                save, detailsStatus);
        section.setPadding(false);
        section.setSpacing(true);

        if (isLotteryEvent) {
            HorizontalLayout lotteryRow = new HorizontalLayout(lotteryOpenPicker, lotteryClosePicker);
            lotteryRow.setWidthFull();
            Button saveLottery = new Button("Save lottery window", e -> saveLotteryWindow());
            saveLottery.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            section.add(new H4("Lottery registration window"), lotteryRow, saveLottery);
        }

        return section;
    }

    private void saveDetails() {
        EventActionResult result = presenter.editEvent(
                eventId,
                eventName.getValue(),
                description.getValue(),
                artist.getValue(),
                toInstant(startTime.getValue()),
                toInstant(endTime.getValue()),
                toInstant(doorsOpenTime.getValue()));
        detailsStatus.setText(result.message());
        showResult(result.success(), result.message());
    }

    private void saveLotteryWindow() {
        Instant open = toInstant(lotteryOpenPicker.getValue());
        Instant close = toInstant(lotteryClosePicker.getValue());
        if (open == null || close == null) {
            UiMessages.error("Both registration open and close times are required.");
            return;
        }
        EventActionResult result = presenter.editEvent(
                eventId, null, null, null, null, null, null, open, close);
        detailsStatus.setText(result.message());
        showResult(result.success(), result.message());
    }

    // ── Zones ──

    private VerticalLayout buildZonesSection() {
        zonesPanel.setPadding(false);
        zonesPanel.setSpacing(false);
        renderZones();
        VerticalLayout section = new VerticalLayout(
                new H4("Zones"),
                new Span("Adjust price and GA capacity. Capacity can only drop above already-sold tickets."),
                zonesPanel, zonesStatus);
        section.setPadding(false);
        section.setSpacing(true);
        return section;
    }

    private void renderZones() {
        zonesPanel.removeAll();
        if (zones.isEmpty()) {
            zonesPanel.add(new Span("This event has no zones."));
            return;
        }
        for (ZoneOption zone : zones) {
            zonesPanel.add(buildZoneRow(zone));
        }
    }

    private HorizontalLayout buildZoneRow(ZoneOption zone) {
        Span name = new Span(zone.name + (zone.ga ? " (GA)" : " (Seating)"));
        name.getStyle().set("min-width", "160px");

        BigDecimalField price = new BigDecimalField("Price");
        price.setValue(priceOf(zone.id));
        Button setPrice = new Button("Set price", e -> {
            ActionResult result = presenter.setZonePrice(eventId, zone.id, price.getValue());
            zonesStatus.setText(result.message());
            showResult(result.success(), result.message());
            if (result.success()) {
                reloadZones();
            }
        });

        HorizontalLayout row = new HorizontalLayout(name, price, setPrice);
        row.setAlignItems(FlexComponent.Alignment.BASELINE);
        row.getStyle().set("flex-wrap", "wrap");

        if (zone.ga) {
            IntegerField delta = new IntegerField("Capacity change");
            delta.setValue(10);
            Button increase = new Button("Increase", e -> applyCapacity(zone, delta.getValue(), true));
            Button decrease = new Button("Decrease", e -> applyCapacity(zone, delta.getValue(), false));
            row.add(delta, increase, decrease);
        }
        return row;
    }

    private void applyCapacity(ZoneOption zone, Integer delta, boolean increase) {
        ActionResult result = increase
                ? presenter.increaseGACapacity(eventId, zone.id, delta)
                : presenter.decreaseGACapacity(eventId, zone.id, delta);
        zonesStatus.setText(result.message());
        showResult(result.success(), result.message());
        if (result.success()) {
            reloadZones();
        }
    }

    private void reloadZones() {
        loadEventMap();
        renderZones();
    }

    private BigDecimal priceOf(UUID zoneId) {
        return zones.stream().filter(z -> z.id.equals(zoneId)).findFirst()
                .map(z -> currentPrices.getOrDefault(z.id, BigDecimal.ZERO))
                .orElse(BigDecimal.ZERO);
    }

    private final java.util.Map<UUID, BigDecimal> currentPrices = new java.util.HashMap<>();

    // ── Layout ──

    private VerticalLayout buildLayoutSection() {
        VerticalLayout layoutSection = new VerticalLayout();
        layoutSection.setPadding(false);
        layoutSection.setSpacing(true);

        layoutSection.add(new H4("Hall layout"));
        boolean editable = status == com.ticketing.domain.event.EventStatus.DRAFT;
        if (!editable) {
            layoutSection.add(new Span("The layout is locked once the event is published."));
            return layoutSection;
        }

        Button saveLayout = new Button("Save layout", e -> saveVenue());
        saveLayout.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button validate = new Button("Validate layout", e -> validateLayout());
        HorizontalLayout layoutActions = new HorizontalLayout(saveLayout, validate);

        layoutSection.add(layoutEditor, layoutActions, layoutStatus);
        return layoutSection;
    }



    private void saveVenue() {
        try {
            VenueLayoutEditorComponent.EditorResult r = layoutEditor.buildResult();
            EventActionResult result = presenter.defineVenue(
                    eventId, companyName, null, null, null, null, null, null, null,
                    r.rows(), r.cols(), r.zoneSpecs(), r.sectionToZone(), r.cellSpecs(),
                    null, null);
            layoutStatus.setText(result.message());
            showResult(result.success(), result.message());
            if (result.success()) {
                rerenderSections();
            }
        } catch (IllegalStateException ex) {
            UiMessages.error(ex.getMessage());
        }
    }

    private void rerenderSections() {
        loadEventMap();
        body.removeAll();
        body.add(
                buildDetailsSection(),
                buildZonesSection(),
                buildLayoutSection(),
                buildLifecycleSection());
    }

    private static String rowLetter(int r) {
        return String.valueOf((char) ('A' + (r % 26))) + (r >= 26 ? Integer.toString(r / 26) : "");
    }

    private void validateLayout() {
        ActionResult result = presenter.validateEventLayout(eventId);
        layoutStatus.setText(result.message());
        showResult(result.success(), result.message());
    }

    // ── Lifecycle ──

    private VerticalLayout buildLifecycleSection() {
        Button publish = new Button("Publish event", e -> {
            ActionResult result = presenter.publishEvent(eventId);
            lifecycleStatus.setText(result.message());
            showResult(result.success(), result.message());
        });
        publish.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        publish.setEnabled(status == EventStatus.DRAFT);

        Button cancel = new Button("Cancel event", e ->
                DestructiveActionDialogs.confirmCancelEvent(eventName.getValue(), () -> {
                    ActionResult result = presenter.cancelEvent(eventId);
                    lifecycleStatus.setText(result.message());
                    showResult(result.success(), result.message());
                }));
        cancel.addThemeVariants(ButtonVariant.LUMO_ERROR);

        HorizontalLayout actions = new HorizontalLayout(publish, cancel);
        actions.setAlignItems(FlexComponent.Alignment.BASELINE);
        VerticalLayout section = new VerticalLayout(new H4("Lifecycle"), actions, lifecycleStatus);
        section.setPadding(false);
        section.setSpacing(true);
        return section;
    }

    private void loadEventMap() {
        EventMapResult result = presenter.loadEventMapForManagement(eventId);
        zones.clear();
        currentPrices.clear();
        if (result == null || !result.success() || result.eventMap() == null) {
            layoutEditor.setEventMap(null);
            return;
        }
        EventMapDTO map = result.eventMap();
        this.status = map.status();
        description.setValue(nullToEmpty(map.description()));
        for (EventMapDTO.ZoneInfo zone : map.zones()) {
            boolean ga = zone.type() == ZoneType.GENERAL_ADMISSION;
            int capacity = zone.maxCapacity() == null ? 0 : zone.maxCapacity();
            zones.add(new ZoneOption(zone.id(), zone.name(), ga, zone.pricePerTicket(), capacity));
            currentPrices.put(zone.id(), zone.pricePerTicket());
        }
        layoutEditor.setEventMap(map);
        if (isLotteryEvent && map.lotteryInfo() != null) {
            lotteryOpenPicker.setValue(toLocal(map.lotteryInfo().registrationOpen()));
            lotteryClosePicker.setValue(toLocal(map.lotteryInfo().registrationClose()));
        }
    }

    // ── Helpers ──

    private static void showResult(boolean success, String message) {
        if (success) {
            UiMessages.success(message);
        } else {
            UiMessages.error(message);
        }
    }

    private static int valueOr(IntegerField field, int fallback) {
        Integer v = field.getValue();
        return v == null ? fallback : v;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt == null ? null : ldt.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static LocalDateTime toLocal(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
