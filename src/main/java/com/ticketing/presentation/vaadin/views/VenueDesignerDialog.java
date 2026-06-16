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
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.LayoutCellType;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventActionResult;
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
 * Unified visual venue designer — combines zone configuration and hall layout painting
 * into a single Ticketmaster-style experience. Supports multiple seating and GA zones
 * at different prices, each color-coded on the grid.
 */
public class VenueDesignerDialog extends Dialog {

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

    private enum ZoneKind { SEATING, GA }

    private static class ZoneDefinition {
        final String name;
        final ZoneKind kind;
        final BigDecimal price;
        final int gaCapacity;
        final String color;

        ZoneDefinition(String name, ZoneKind kind, BigDecimal price, int gaCapacity, String color) {
            this.name = name;
            this.kind = kind;
            this.price = price;
            this.gaCapacity = gaCapacity;
            this.color = color;
        }
    }

    private record CellState(ZoneDefinition zone, StructuralTool structural, String label) {
        boolean isZone() { return zone != null; }
        boolean isStructural() { return structural != null; }
    }

    private final CompanyPresenter presenter;
    private final String companyName;

    // Event metadata
    private final TextField eventName = new TextField("Event name");
    private final TextField description = new TextField("Description");
    private final ComboBox<EventCategory> category = new ComboBox<>("Category");
    private final DateTimePicker startTime = new DateTimePicker("Start time");
    private final DateTimePicker endTime = new DateTimePicker("End time");
    private final DateTimePicker doorsOpenTime = new DateTimePicker("Doors open time");
    private final IntegerField lockMinutes = new IntegerField("Lock minutes");

    // Grid controls
    private final IntegerField rowsField = new IntegerField("Rows");
    private final IntegerField colsField = new IntegerField("Columns");
    private final TextField cellLabel = new TextField("Label (stage/object)");

    // Zone creation form
    private final TextField newZoneName = new TextField("Zone name");
    private final ComboBox<String> newZoneKind = new ComboBox<>("Type");
    private final BigDecimalField newZonePrice = new BigDecimalField("Price");
    private final IntegerField newZoneGaCapacity = new IntegerField("GA capacity");

    private final Div grid = new Div();
    private final Span status = new Span("Create zones, build a grid, paint cells, then Save draft.");
    private final Button saveDraft = new Button("Save draft", e -> saveDraft());
    private final Button validate = new Button("Validate", e -> validateLayout());
    private final Button publish = new Button("Publish", e -> publish());

    // Zone palette
    private final VerticalLayout zonePaletteList = new VerticalLayout();
    private final VerticalLayout zoneSummaryPanel = new VerticalLayout();

    // State
    private final List<ZoneDefinition> zones = new ArrayList<>();
    private CellState[][] cellStates = new CellState[0][0];
    private Button[][] cellButtons = new Button[0][0];
    private ZoneDefinition activeZone;
    private StructuralTool activeStructural;
    private boolean eraseMode;
    private UUID eventId;
    private int colorIndex;

    public VenueDesignerDialog(CompanyPresenter presenter, String companyName) {
        this.presenter = presenter;
        this.companyName = companyName;

        setHeaderTitle("Venue designer — " + companyName);
        setWidth("1100px");

        category.setItems(EventCategory.values());
        category.setItemLabelGenerator(EventCategory::name);
        category.setValue(EventCategory.CONCERT);
        lockMinutes.setMin(1);
        lockMinutes.setValue(15);
        rowsField.setMin(1);
        rowsField.setValue(5);
        colsField.setMin(1);
        colsField.setValue(8);

        newZoneKind.setItems("Seating", "GA");
        newZoneKind.setValue("Seating");
        newZonePrice.setValue(new BigDecimal("100.00"));
        newZoneGaCapacity.setMin(1);
        newZoneGaCapacity.setValue(100);
        newZoneGaCapacity.setVisible(false);
        newZoneKind.addValueChangeListener(e -> newZoneGaCapacity.setVisible("GA".equals(e.getValue())));

        RequiredFields.markRequired(eventName, "Event name is required.");
        RequiredFields.markRequired(startTime, "Start time is required.");
        RequiredFields.markRequired(endTime, "End time is required.");

        // --- Left sidebar: zone palette ---
        VerticalLayout leftSidebar = buildZonePalette();
        leftSidebar.setWidth("260px");
        leftSidebar.getStyle().set("min-width", "260px").set("flex-shrink", "0");

        // --- Center: grid area ---
        VerticalLayout centerPanel = buildCenterPanel();
        centerPanel.getStyle().set("flex", "1 1 auto").set("min-width", "0");

        // --- Right sidebar: zone summary ---
        VerticalLayout rightSidebar = buildSummaryPanel();
        rightSidebar.setWidth("200px");
        rightSidebar.getStyle().set("min-width", "200px").set("flex-shrink", "0");

        // Event metadata rows
        HorizontalLayout metadataRow1 = new HorizontalLayout(eventName, category, lockMinutes);
        metadataRow1.setWidthFull();
        metadataRow1.setAlignItems(FlexComponent.Alignment.BASELINE);
        HorizontalLayout metadataRow2 = new HorizontalLayout(startTime, endTime, doorsOpenTime);
        metadataRow2.setWidthFull();
        metadataRow2.setAlignItems(FlexComponent.Alignment.BASELINE);

        HorizontalLayout mainLayout = new HorizontalLayout(leftSidebar, centerPanel, rightSidebar);
        mainLayout.setWidthFull();
        mainLayout.setSpacing(true);
        mainLayout.getStyle().set("min-height", "400px");

        VerticalLayout content = new VerticalLayout(
                new Span("1) Event details"),
                metadataRow1, metadataRow2, description,
                new Span("2) Design venue"),
                mainLayout,
                status
        );
        content.setPadding(false);
        content.setSpacing(true);
        add(content);

        validate.setEnabled(false);
        publish.setEnabled(false);
        Button close = new Button("Close", e -> close());
        getFooter().add(close, saveDraft, validate, publish);

        buildGrid();
        refreshZonePalette();
        refreshZoneSummary();
    }

    private VerticalLayout buildZonePalette() {
        H4 header = new H4("Zones");
        header.getStyle().set("margin", "0");

        HorizontalLayout nameAndKind = new HorizontalLayout(newZoneName, newZoneKind);
        nameAndKind.setWidthFull();
        HorizontalLayout priceAndCap = new HorizontalLayout(newZonePrice, newZoneGaCapacity);
        priceAndCap.setWidthFull();
        Button addZoneBtn = new Button("Add zone", e -> addZone());
        addZoneBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        newZoneName.setWidthFull();
        newZoneKind.setWidth("100px");
        newZonePrice.setWidthFull();
        newZoneGaCapacity.setWidthFull();

        zonePaletteList.setPadding(false);
        zonePaletteList.setSpacing(false);
        zonePaletteList.setWidthFull();

        H4 toolsHeader = new H4("Tools");
        toolsHeader.getStyle().set("margin", "0");
        Button stageBtn = toolButton("Stage", StructuralTool.STAGE);
        Button objectBtn = toolButton("Object", StructuralTool.OBJECT);
        Button blockedBtn = toolButton("Blocked", StructuralTool.BLOCKED);
        Button eraseBtn = new Button("Erase", e -> selectErase());
        eraseBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        HorizontalLayout tools = new HorizontalLayout(stageBtn, objectBtn, blockedBtn, eraseBtn);
        tools.setWidthFull();

        VerticalLayout sidebar = new VerticalLayout(
                header, nameAndKind, priceAndCap, addZoneBtn,
                zonePaletteList,
                toolsHeader, tools, cellLabel
        );
        sidebar.setPadding(false);
        sidebar.setSpacing(true);
        return sidebar;
    }

    private Button toolButton(String text, StructuralTool tool) {
        Button btn = new Button(text, e -> selectStructuralTool(tool));
        btn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        return btn;
    }

    private VerticalLayout buildCenterPanel() {
        Button buildGridBtn = new Button("Build grid", e -> buildGrid());
        buildGridBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        HorizontalLayout gridControls = new HorizontalLayout(rowsField, colsField, buildGridBtn);
        gridControls.setAlignItems(FlexComponent.Alignment.BASELINE);

        grid.getStyle().set("overflow", "auto").set("max-height", "360px").set("padding", "4px");

        VerticalLayout center = new VerticalLayout(gridControls, grid);
        center.setPadding(false);
        center.setSpacing(true);
        return center;
    }

    private VerticalLayout buildSummaryPanel() {
        H4 header = new H4("Summary");
        header.getStyle().set("margin", "0");
        zoneSummaryPanel.setPadding(false);
        zoneSummaryPanel.setSpacing(false);
        zoneSummaryPanel.setWidthFull();

        VerticalLayout sidebar = new VerticalLayout(header, zoneSummaryPanel);
        sidebar.setPadding(false);
        sidebar.setSpacing(true);
        return sidebar;
    }

    // ── Zone management ──

    private void addZone() {
        String name = newZoneName.getValue();
        if (name == null || name.isBlank()) {
            UiMessages.error("Zone name is required.");
            return;
        }
        name = name.trim();
        BigDecimal price = newZonePrice.getValue();
        if (price == null || price.signum() < 0) {
            UiMessages.error("Price must be non-negative.");
            return;
        }
        for (ZoneDefinition z : zones) {
            if (z.name.equalsIgnoreCase(name)) {
                UiMessages.error("A zone named '" + name + "' already exists.");
                return;
            }
        }
        ZoneKind kind = "GA".equals(newZoneKind.getValue()) ? ZoneKind.GA : ZoneKind.SEATING;
        int capacity = 0;
        if (kind == ZoneKind.GA) {
            Integer cap = newZoneGaCapacity.getValue();
            if (cap == null || cap <= 0) {
                UiMessages.error("GA capacity must be positive.");
                return;
            }
            capacity = cap;
        }
        String color = ZONE_COLORS[colorIndex % ZONE_COLORS.length];
        colorIndex++;

        ZoneDefinition zone = new ZoneDefinition(name, kind, price, capacity, color);
        zones.add(zone);
        selectZone(zone);
        newZoneName.clear();
        refreshZonePalette();
        refreshZoneSummary();
        UiMessages.info("Zone '" + name + "' added. Click grid cells to assign them.");
    }

    private void removeZone(ZoneDefinition zone) {
        zones.remove(zone);
        if (activeZone == zone) {
            activeZone = null;
        }
        for (int r = 0; r < cellStates.length; r++) {
            for (int c = 0; c < cellStates[r].length; c++) {
                if (cellStates[r][c] != null && cellStates[r][c].zone == zone) {
                    cellStates[r][c] = null;
                    repaintCell(r, c);
                }
            }
        }
        refreshZonePalette();
        refreshZoneSummary();
    }

    private void selectZone(ZoneDefinition zone) {
        activeZone = zone;
        activeStructural = null;
        eraseMode = false;
        refreshZonePalette();
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

    private void refreshZonePalette() {
        zonePaletteList.removeAll();
        if (zones.isEmpty()) {
            zonePaletteList.add(new Span("No zones yet."));
            return;
        }
        for (ZoneDefinition zone : zones) {
            Div colorSwatch = new Div();
            colorSwatch.getStyle()
                    .set("width", "16px").set("height", "16px")
                    .set("background", zone.color)
                    .set("border-radius", "3px").set("flex-shrink", "0");

            String kindLabel = zone.kind == ZoneKind.GA ? "GA" : "Seats";
            Span label = new Span(zone.name + " (" + kindLabel + ") $" + zone.price.toPlainString());
            label.getStyle().set("font-size", "var(--lumo-font-size-s)").set("flex", "1");

            Button selectBtn = new Button("Select", e -> selectZone(zone));
            selectBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            if (activeZone == zone) {
                selectBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                selectBtn.setText("Active");
            }
            Button removeBtn = new Button("×", e -> removeZone(zone));
            removeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);

            HorizontalLayout row = new HorizontalLayout(colorSwatch, label, selectBtn, removeBtn);
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            row.setWidthFull();
            row.getStyle().set("padding", "2px 0");
            zonePaletteList.add(row);
        }
    }

    private void refreshZoneSummary() {
        zoneSummaryPanel.removeAll();
        if (zones.isEmpty()) {
            zoneSummaryPanel.add(new Span("Add zones to see summary."));
            return;
        }
        int totalSeats = 0;
        int totalGa = 0;
        for (ZoneDefinition zone : zones) {
            int cellCount = countCellsForZone(zone);
            String detail;
            if (zone.kind == ZoneKind.SEATING) {
                totalSeats += cellCount;
                detail = zone.name + ": " + cellCount + " seats, $" + zone.price.toPlainString();
            } else {
                totalGa += zone.gaCapacity;
                detail = zone.name + ": cap " + zone.gaCapacity + " (" + cellCount + " cells), $" + zone.price.toPlainString();
            }
            Div colorDot = new Div();
            colorDot.getStyle()
                    .set("width", "10px").set("height", "10px")
                    .set("background", zone.color).set("border-radius", "50%")
                    .set("flex-shrink", "0");
            HorizontalLayout row = new HorizontalLayout(colorDot, new Span(detail));
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            row.getStyle().set("font-size", "var(--lumo-font-size-s)");
            zoneSummaryPanel.add(row);
        }
        Span total = new Span("Total: " + totalSeats + " seats + " + totalGa + " GA");
        total.getStyle().set("font-weight", "bold").set("font-size", "var(--lumo-font-size-s)").set("margin-top", "8px");
        zoneSummaryPanel.add(total);
    }

    private int countCellsForZone(ZoneDefinition zone) {
        int count = 0;
        for (CellState[] row : cellStates) {
            for (CellState state : row) {
                if (state != null && state.zone == zone) count++;
            }
        }
        return count;
    }

    // ── Grid ──

    private void buildGrid() {
        int rows = valueOr(rowsField, 5);
        int cols = valueOr(colsField, 8);
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
        eventId = null;
        validate.setEnabled(false);
        publish.setEnabled(false);
        String ready = "Grid " + rows + "×" + cols + " ready. Select a zone and click cells.";
        status.setText(ready);
        UiMessages.info(ready);
    }

    private void paint(int r, int c) {
        if (eraseMode) {
            cellStates[r][c] = null;
            repaintCell(r, c);
            refreshZoneSummary();
            return;
        }
        if (activeStructural != null) {
            String label = blankToNull(cellLabel.getValue());
            cellStates[r][c] = new CellState(null, activeStructural, label);
            repaintCell(r, c);
            return;
        }
        if (activeZone != null) {
            cellStates[r][c] = new CellState(activeZone, null, null);
            repaintCell(r, c);
            refreshZoneSummary();
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
            String glyph = state.zone.kind == ZoneKind.SEATING ? "S" : "G";
            btn.setText(glyph);
            btn.getStyle().set("background", state.zone.color).set("color", "white");
        } else {
            btn.setText(state.structural.glyph);
            btn.getStyle().set("background", state.structural.color).set("color", "white");
        }
    }

    // ── Save / Validate / Publish ──

    private void saveDraft() {
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

        for (ZoneDefinition zone : zones) {
            sectionToZone.put(zone.name, zone.name);
            if (zone.kind == ZoneKind.SEATING) {
                List<CreateEventRequest.SeatSpec> seatSpecs = new ArrayList<>();
                for (int r = 0; r < cellStates.length; r++) {
                    for (int c = 0; c < cellStates[r].length; c++) {
                        CellState s = cellStates[r][c];
                        if (s != null && s.zone == zone) {
                            seatSpecs.add(new CreateEventRequest.SeatSpec(rowLetter(r), String.valueOf(c + 1)));
                        }
                    }
                }
                if (seatSpecs.isEmpty()) continue;
                zoneSpecs.add(new CreateEventRequest.AssignedZoneSpec(zone.name, zone.price, seatSpecs));
            } else {
                boolean hasCells = false;
                for (CellState[] row : cellStates) {
                    for (CellState s : row) {
                        if (s != null && s.zone == zone) { hasCells = true; break; }
                    }
                    if (hasCells) break;
                }
                if (!hasCells) continue;
                zoneSpecs.add(new CreateEventRequest.GAZoneSpec(zone.name, zone.price, zone.gaCapacity));
            }
        }

        if (zoneSpecs.isEmpty()) {
            UiMessages.error("Paint at least one zone cell on the grid.");
            return;
        }

        // Build linked layout cell specs from the full grid.
        List<DefineVenueRequest.CellSpec> cellSpecs = new ArrayList<>();
        for (int r = 0; r < cellStates.length; r++) {
            for (int c = 0; c < cellStates[r].length; c++) {
                CellState s = cellStates[r][c];
                if (s == null) continue;
                if (s.isZone()) {
                    if (s.zone.kind == ZoneKind.SEATING) {
                        cellSpecs.add(new DefineVenueRequest.CellSpec(r, c, LayoutCellType.SEAT,
                                null, s.zone.name, rowLetter(r), String.valueOf(c + 1)));
                    } else {
                        cellSpecs.add(new DefineVenueRequest.CellSpec(r, c, LayoutCellType.GENERAL_ADMISSION,
                                s.zone.name, s.zone.name, null, null));
                    }
                } else {
                    LayoutCellType type = switch (s.structural) {
                        case BLOCKED -> LayoutCellType.BLOCKED;
                        case STAGE -> LayoutCellType.STAGE;
                        case OBJECT -> LayoutCellType.OBJECT;
                    };
                    cellSpecs.add(new DefineVenueRequest.CellSpec(r, c, type, s.label, null, null, null));
                }
            }
        }

        Instant start = toInstant(startTime.getValue());
        if (start == null) {
            UiMessages.error("Pick a start time.");
            return;
        }
        Instant end = toInstant(endTime.getValue());
        if (end == null) {
            UiMessages.error("Pick an end time.");
            return;
        }
        Instant doors = toInstant(doorsOpenTime.getValue());

        EventActionResult result = presenter.defineVenue(
                null, companyName, eventName.getValue(), description.getValue(), category.getValue(),
                start, end, doors, lockMinutes.getValue(),
                cellStates.length, cellStates[0].length, zoneSpecs, sectionToZone, cellSpecs);
        if (!result.success() || result.eventId() == null) {
            status.setText("Save failed: " + result.message());
            UiMessages.error(result.message());
            return;
        }

        eventId = result.eventId();
        validate.setEnabled(true);
        publish.setEnabled(true);
        int totalSeats = zoneSpecs.stream()
                .filter(z -> z instanceof CreateEventRequest.AssignedZoneSpec)
                .mapToInt(z -> ((CreateEventRequest.AssignedZoneSpec) z).seats().size())
                .sum();
        status.setText("Draft saved (" + totalSeats + " seats, " + zoneSpecs.size()
                + " zones, " + cellSpecs.size() + " cells). Validate, then Publish.");
        UiMessages.success("Draft saved.");
    }

    private void validateLayout() {
        if (eventId == null) {
            UiMessages.error("Save a draft first.");
            return;
        }
        ActionResult result = presenter.validateEventLayout(eventId);
        status.setText(result.message());
        showResult(result.success(), result.message());
    }

    private void publish() {
        if (eventId == null) {
            UiMessages.error("Save a draft first.");
            return;
        }
        ActionResult result = presenter.publishEvent(eventId);
        status.setText(result.message());
        showResult(result.success(), result.message());
        if (result.success()) {
            publish.setEnabled(false);
        }
    }

    private static void showResult(boolean success, String message) {
        if (success) {
            UiMessages.success(message);
        } else {
            UiMessages.error(message);
        }
    }

    private static String rowLetter(int r) {
        return String.valueOf((char) ('A' + (r % 26))) + (r >= 26 ? Integer.toString(r / 26) : "");
    }

    private static int valueOr(IntegerField field, int fallback) {
        Integer v = field.getValue();
        return v == null ? fallback : v;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt == null ? null : ldt.atZone(ZoneId.systemDefault()).toInstant();
    }
}
