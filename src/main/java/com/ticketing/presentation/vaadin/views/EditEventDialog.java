package com.ticketing.presentation.vaadin.views;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketing.application.CreateEventRequest;
import com.ticketing.application.DefineVenueRequest;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.LayoutCellType;
import com.ticketing.domain.event.SaleMethod;
import com.ticketing.domain.event.ZoneType;
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
import com.vaadin.flow.component.html.Div;
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

    private static final String[] ZONE_COLORS = {
            "#1976d2", "#2e7d32", "#d32f2f", "#f57c00", "#7b1fa2",
            "#00838f", "#c2185b", "#6d4c41", "#1565c0", "#558b2f", "#ad1457", "#455a64"
    };
    private static final String BLOCKED_COLOR = "#616161";
    private static final String STAGE_COLOR = "#6a1b9a";
    private static final String OBJECT_COLOR = "#ef6c00";
    private static final String EMPTY_BG = "var(--lumo-contrast-5pct)";

    private enum StructuralTool {
        BLOCKED("X", BLOCKED_COLOR), STAGE("ST", STAGE_COLOR), OBJECT("O", OBJECT_COLOR);
        final String glyph;
        final String color;
        StructuralTool(String glyph, String color) { this.glyph = glyph; this.color = color; }
    }

    private static final class ZoneOption {
        final UUID id;
        final String name;
        final boolean ga;
        final BigDecimal price;
        final int gaCapacity;
        final String color;
        ZoneOption(UUID id, String name, boolean ga, BigDecimal price, int gaCapacity, String color) {
            this.id = id;
            this.name = name;
            this.ga = ga;
            this.price = price;
            this.gaCapacity = gaCapacity;
            this.color = color;
        }
    }

    private record CellState(ZoneOption zone, StructuralTool structural, String label) {
        boolean isZone() { return zone != null; }
    }

    private final CompanyPresenter presenter;
    private final String companyName;
    private UUID eventId;
    private EventStatus status;

    // Event selection
    private final ComboBox<EventSummaryDTO> eventPicker = new ComboBox<>("Event to manage");
    private final VerticalLayout body = new VerticalLayout();

    // Event details
    private final TextField eventName = new TextField("Event name");
    private final TextField description = new TextField("Description");
    private final TextField artist = new TextField("Artist");
    private final DateTimePicker startTime = new DateTimePicker("Start time");
    private final DateTimePicker endTime = new DateTimePicker("End time");
    private final DateTimePicker doorsOpenTime = new DateTimePicker("Doors open time");
    private final Span detailsStatus = new Span();

    // Zones
    private final VerticalLayout zonesPanel = new VerticalLayout();
    private final Span zonesStatus = new Span();

    // Layout editor
    private final IntegerField rowsField = new IntegerField("Rows");
    private final IntegerField colsField = new IntegerField("Columns");
    private final TextField cellLabel = new TextField("Label (stage/object)");
    private final Div grid = new Div();
    private final VerticalLayout zonePaletteList = new VerticalLayout();
    private final Span layoutStatus = new Span();

    // Zone creation (layout editor palette)
    private final TextField newZoneName = new TextField("Zone name");
    private final ComboBox<String> newZoneKind = new ComboBox<>("Type");
    private final BigDecimalField newZonePrice = new BigDecimalField("Price");
    private final IntegerField newZoneGaCapacity = new IntegerField("GA capacity");
    private int colorIndex;

    // Lottery window
    private final DateTimePicker lotteryOpenPicker = new DateTimePicker("Registration opens");
    private final DateTimePicker lotteryClosePicker = new DateTimePicker("Registration closes");
    private boolean isLotteryEvent;

    // Lifecycle
    private final Span lifecycleStatus = new Span();

    // State
    private final List<ZoneOption> zones = new ArrayList<>();
    private CellState[][] cellStates = new CellState[0][0];
    private Button[][] cellButtons = new Button[0][0];
    private ZoneOption activeZone;
    private StructuralTool activeStructural;
    private boolean eraseMode;

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
        boolean editable = status == EventStatus.DRAFT;
        if (!editable) {
            layoutSection.add(new Span("The layout is locked once the event is published."));
            return layoutSection;
        }

        layoutSection.add(new Span(
                "Repaint the hall: add zones, build a grid, paint seats/GA, then Save layout. "
                        + "This rebuilds the event's tickets to match."));

        rowsField.setMin(1);
        colsField.setMin(1);

        Button buildGridBtn = new Button("Build grid", e -> buildGrid());
        buildGridBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        HorizontalLayout gridControls = new HorizontalLayout(rowsField, colsField, buildGridBtn);
        gridControls.setAlignItems(FlexComponent.Alignment.BASELINE);

        newZoneKind.setItems("Seating", "GA");
        newZoneKind.setValue("Seating");
        newZonePrice.setValue(new BigDecimal("50.00"));
        newZoneGaCapacity.setMin(1);
        newZoneGaCapacity.setValue(100);
        newZoneGaCapacity.setVisible(false);
        newZoneKind.addValueChangeListener(e -> newZoneGaCapacity.setVisible("GA".equals(e.getValue())));
        Button addZoneBtn = new Button("Add zone", e -> addZone());
        addZoneBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout zoneForm = new HorizontalLayout(
                newZoneName, newZoneKind, newZonePrice, newZoneGaCapacity, addZoneBtn);
        zoneForm.setAlignItems(FlexComponent.Alignment.BASELINE);
        zoneForm.getStyle().set("flex-wrap", "wrap");

        Button stageBtn = toolButton("Stage", StructuralTool.STAGE);
        Button objectBtn = toolButton("Object", StructuralTool.OBJECT);
        Button blockedBtn = toolButton("Blocked", StructuralTool.BLOCKED);
        Button eraseBtn = new Button("Erase", e -> selectErase());
        eraseBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        HorizontalLayout tools = new HorizontalLayout(stageBtn, objectBtn, blockedBtn, eraseBtn, cellLabel);
        tools.setAlignItems(FlexComponent.Alignment.BASELINE);

        zonePaletteList.setPadding(false);
        zonePaletteList.setSpacing(false);

        grid.getStyle().set("overflow", "auto").set("max-height", "360px").set("padding", "4px");

        Button saveLayout = new Button("Save layout", e -> saveVenue());
        saveLayout.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button validate = new Button("Validate layout", e -> validateLayout());
        HorizontalLayout layoutActions = new HorizontalLayout(saveLayout, validate);

        layoutSection.add(gridControls, zoneForm, zonePaletteList, tools, grid, layoutActions, layoutStatus);

        prefillGrid();
        refreshZonePalette();
        return layoutSection;
    }

    private void addZone() {
        String name = blankToNull(newZoneName.getValue());
        if (name == null) {
            UiMessages.error("Zone name is required.");
            return;
        }
        for (ZoneOption z : zones) {
            if (z.name.equalsIgnoreCase(name)) {
                UiMessages.error("A zone named '" + name + "' already exists.");
                return;
            }
        }
        BigDecimal price = newZonePrice.getValue();
        if (price == null || price.signum() < 0) {
            UiMessages.error("Price must be non-negative.");
            return;
        }
        boolean ga = "GA".equals(newZoneKind.getValue());
        int capacity = 0;
        if (ga) {
            Integer cap = newZoneGaCapacity.getValue();
            if (cap == null || cap <= 0) {
                UiMessages.error("GA capacity must be positive.");
                return;
            }
            capacity = cap;
        }
        ZoneOption zone = new ZoneOption(UUID.randomUUID(), name, ga, price, capacity,
                ZONE_COLORS[colorIndex++ % ZONE_COLORS.length]);
        zones.add(zone);
        selectZone(zone);
        newZoneName.clear();
        refreshZonePalette();
        UiMessages.info("Zone '" + name + "' added. Paint its cells on the grid.");
    }

    private void removeZone(ZoneOption zone) {
        zones.remove(zone);
        if (activeZone == zone) {
            activeZone = null;
        }
        for (int r = 0; r < cellStates.length; r++) {
            for (int c = 0; c < cellStates[r].length; c++) {
                if (cellStates[r][c] != null && cellStates[r][c].zone() == zone) {
                    cellStates[r][c] = null;
                    repaintCell(r, c);
                }
            }
        }
        refreshZonePalette();
    }

    private Button toolButton(String text, StructuralTool tool) {
        Button btn = new Button(text, e -> selectStructuralTool(tool));
        btn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        return btn;
    }

    private void selectStructuralTool(StructuralTool tool) {
        activeZone = null;
        activeStructural = tool;
        eraseMode = false;
        refreshZonePalette();
    }

    private void selectErase() {
        activeZone = null;
        activeStructural = null;
        eraseMode = true;
        refreshZonePalette();
    }

    private void selectZone(ZoneOption zone) {
        activeZone = zone;
        activeStructural = null;
        eraseMode = false;
        refreshZonePalette();
    }

    private void refreshZonePalette() {
        zonePaletteList.removeAll();
        if (zones.isEmpty()) {
            zonePaletteList.add(new Span("No zones to paint."));
            return;
        }
        for (ZoneOption zone : zones) {
            Div swatch = new Div();
            swatch.getStyle().set("width", "16px").set("height", "16px")
                    .set("background", zone.color).set("border-radius", "3px").set("flex-shrink", "0");
            Span label = new Span(zone.name + (zone.ga ? " (GA)" : " (Seats)"));
            label.getStyle().set("flex", "1").set("font-size", "var(--lumo-font-size-s)");
            Button select = new Button(activeZone == zone ? "Active" : "Select", e -> selectZone(zone));
            select.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            if (activeZone == zone) {
                select.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            }
            Button remove = new Button("×", e -> removeZone(zone));
            remove.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            HorizontalLayout row = new HorizontalLayout(swatch, label, select, remove);
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            row.setWidthFull();
            zonePaletteList.add(row);
        }
    }

    private void buildGrid() {
        int rows = valueOr(rowsField, 5);
        int cols = valueOr(colsField, 8);
        rebuildGrid(rows, cols);
        String ready = "Grid " + rows + "×" + cols + " ready. Select a zone or tool and click cells.";
        layoutStatus.setText(ready);
        UiMessages.info(ready);
    }

    private void rebuildGrid(int rows, int cols) {
        cellStates = new CellState[rows][cols];
        cellButtons = new Button[rows][cols];
        grid.removeAll();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(" + cols + ", 30px)")
                .set("gap", "2px");
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Button cell = new Button();
                cell.getStyle()
                        .set("min-width", "30px").set("width", "30px").set("height", "30px")
                        .set("padding", "0").set("border", "1px solid var(--lumo-contrast-30pct)")
                        .set("background", EMPTY_BG).set("font-size", "10px");
                final int rr = r, cc = c;
                cell.addClickListener(e -> paint(rr, cc));
                cellButtons[r][c] = cell;
                grid.add(cell);
            }
        }
    }

    private void prefillGrid() {
        if (savedLayout == null || savedLayout.cells().isEmpty()) {
            rowsField.setValue(5);
            colsField.setValue(8);
            rebuildGrid(5, 8);
            return;
        }
        int rows = savedLayout.rows();
        int cols = savedLayout.cols();
        rowsField.setValue(rows);
        colsField.setValue(cols);
        rebuildGrid(rows, cols);
        ZoneOption firstSeating = zones.stream().filter(z -> !z.ga).findFirst().orElse(null);
        for (EventMapDTO.CellInfo cell : savedLayout.cells()) {
            if (cell.row() >= rows || cell.col() >= cols) {
                continue;
            }
            CellState state = toCellState(cell, firstSeating);
            if (state != null) {
                cellStates[cell.row()][cell.col()] = state;
                repaintCell(cell.row(), cell.col());
            }
        }
    }

    private CellState toCellState(EventMapDTO.CellInfo cell, ZoneOption firstSeating) {
        return switch (cell.type()) {
            case STAGE -> new CellState(null, StructuralTool.STAGE, cell.label());
            case OBJECT -> new CellState(null, StructuralTool.OBJECT, cell.label());
            case BLOCKED -> new CellState(null, StructuralTool.BLOCKED, null);
            case GENERAL_ADMISSION -> {
                ZoneOption zone = zoneByLabelOrId(cell.label(), cell.zoneId(), true);
                yield zone == null ? null : new CellState(zone, null, null);
            }
            case SEAT -> {
                ZoneOption zone = cell.zoneId() != null
                        ? zoneById(cell.zoneId())
                        : firstSeating;
                yield zone == null ? null : new CellState(zone, null, null);
            }
        };
    }

    private ZoneOption zoneById(UUID id) {
        return zones.stream().filter(z -> z.id.equals(id)).findFirst().orElse(null);
    }

    private ZoneOption zoneByLabelOrId(String label, UUID id, boolean ga) {
        if (id != null) {
            ZoneOption byId = zoneById(id);
            if (byId != null) {
                return byId;
            }
        }
        return zones.stream()
                .filter(z -> z.ga == ga && z.name.equals(label))
                .findFirst().orElse(null);
    }

    private void paint(int r, int c) {
        if (eraseMode) {
            cellStates[r][c] = null;
            repaintCell(r, c);
            return;
        }
        if (activeStructural != null) {
            cellStates[r][c] = new CellState(null, activeStructural, blankToNull(cellLabel.getValue()));
            repaintCell(r, c);
            return;
        }
        if (activeZone != null) {
            cellStates[r][c] = new CellState(activeZone, null, null);
            repaintCell(r, c);
            return;
        }
        UiMessages.error("Select a zone or tool first.");
    }

    private void repaintCell(int r, int c) {
        Button btn = cellButtons[r][c];
        CellState state = cellStates[r][c];
        if (state == null) {
            btn.setText("");
            btn.getStyle().set("background", EMPTY_BG).set("color", "inherit");
            return;
        }
        if (state.isZone()) {
            btn.setText(state.zone().ga ? "G" : "S");
            btn.getStyle().set("background", state.zone().color).set("color", "white");
        } else {
            btn.setText(state.structural().glyph);
            btn.getStyle().set("background", state.structural().color).set("color", "white");
        }
    }

    private void saveVenue() {
        if (cellStates.length == 0) {
            UiMessages.error("Build a grid first.");
            return;
        }
        if (zones.isEmpty()) {
            UiMessages.error("Add at least one zone before saving.");
            return;
        }

        List<CreateEventRequest.ZoneSpec> zoneSpecs = new ArrayList<>();
        Map<String, String> sectionToZone = new LinkedHashMap<>();
        for (ZoneOption zone : zones) {
            if (zone.ga) {
                if (!hasCellsFor(zone)) {
                    continue;
                }
                sectionToZone.put(zone.name, zone.name);
                zoneSpecs.add(new CreateEventRequest.GAZoneSpec(zone.name, zone.price, zone.gaCapacity));
            } else {
                List<CreateEventRequest.SeatSpec> seatSpecs = new ArrayList<>();
                for (int r = 0; r < cellStates.length; r++) {
                    for (int c = 0; c < cellStates[r].length; c++) {
                        CellState s = cellStates[r][c];
                        if (s != null && s.zone() == zone) {
                            seatSpecs.add(new CreateEventRequest.SeatSpec(rowLetter(r), String.valueOf(c + 1)));
                        }
                    }
                }
                if (seatSpecs.isEmpty()) {
                    continue;
                }
                sectionToZone.put(zone.name, zone.name);
                zoneSpecs.add(new CreateEventRequest.AssignedZoneSpec(zone.name, zone.price, seatSpecs));
            }
        }
        if (zoneSpecs.isEmpty()) {
            UiMessages.error("Paint at least one zone cell on the grid.");
            return;
        }

        List<DefineVenueRequest.CellSpec> cellSpecs = new ArrayList<>();
        for (int r = 0; r < cellStates.length; r++) {
            for (int c = 0; c < cellStates[r].length; c++) {
                CellState s = cellStates[r][c];
                if (s == null) {
                    continue;
                }
                if (s.isZone()) {
                    if (!sectionToZone.containsKey(s.zone().name)) {
                        continue; // zone was skipped (no cells) — keep layout consistent
                    }
                    if (s.zone().ga) {
                        cellSpecs.add(new DefineVenueRequest.CellSpec(r, c, LayoutCellType.GENERAL_ADMISSION,
                                s.zone().name, s.zone().name, null, null));
                    } else {
                        cellSpecs.add(new DefineVenueRequest.CellSpec(r, c, LayoutCellType.SEAT,
                                null, s.zone().name, rowLetter(r), String.valueOf(c + 1)));
                    }
                } else {
                    LayoutCellType type = switch (s.structural()) {
                        case BLOCKED -> LayoutCellType.BLOCKED;
                        case STAGE -> LayoutCellType.STAGE;
                        case OBJECT -> LayoutCellType.OBJECT;
                    };
                    cellSpecs.add(new DefineVenueRequest.CellSpec(r, c, type, s.label(), null, null, null));
                }
            }
        }

        EventActionResult result = presenter.defineVenue(
                eventId, companyName, null, null, null, null, null, null, null,
                cellStates.length, cellStates[0].length, zoneSpecs, sectionToZone, cellSpecs,
                null, null);
        layoutStatus.setText(result.message());
        showResult(result.success(), result.message());
        if (result.success()) {
            rerenderSections();
        }
    }

    private boolean hasCellsFor(ZoneOption zone) {
        for (CellState[] row : cellStates) {
            for (CellState s : row) {
                if (s != null && s.zone() == zone) {
                    return true;
                }
            }
        }
        return false;
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

    // ── Loading ──

    private EventMapDTO.LayoutInfo savedLayout;

    private void loadEventMap() {
        EventMapResult result = presenter.loadEventMapForManagement(eventId);
        zones.clear();
        currentPrices.clear();
        savedLayout = null;
        if (result == null || !result.success() || result.eventMap() == null) {
            return;
        }
        EventMapDTO map = result.eventMap();
        this.status = map.status();
        description.setValue(nullToEmpty(map.description()));
        colorIndex = 0;
        for (EventMapDTO.ZoneInfo zone : map.zones()) {
            boolean ga = zone.type() == ZoneType.GENERAL_ADMISSION;
            int capacity = zone.maxCapacity() == null ? 0 : zone.maxCapacity();
            zones.add(new ZoneOption(zone.id(), zone.name(), ga, zone.pricePerTicket(), capacity,
                    ZONE_COLORS[colorIndex % ZONE_COLORS.length]));
            currentPrices.put(zone.id(), zone.pricePerTicket());
            colorIndex++;
        }
        savedLayout = map.layout();
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
