package com.ticketing.presentation.vaadin.components;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketing.application.CreateEventRequest;
import com.ticketing.application.DefineVenueRequest;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.LayoutCellType;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.ticketing.presentation.vaadin.util.VenueGridDragPainter;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;

public class VenueLayoutEditorComponent extends VerticalLayout {

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

    private final IntegerField rowsField = new IntegerField("Rows");
    private final IntegerField colsField = new IntegerField("Columns");
    private final TextField cellLabel = new TextField("Label (stage/object)");
    private final Div grid = new Div();
    private final VerticalLayout zonePaletteList = new VerticalLayout();
    
    private final TextField newZoneName = new TextField("Zone name");
    private final ComboBox<String> newZoneKind = new ComboBox<>("Type");
    private final BigDecimalField newZonePrice = new BigDecimalField("Price");
    private final IntegerField newZoneGaCapacity = new IntegerField("GA capacity");
    private int colorIndex;

    private final List<ZoneOption> zones = new ArrayList<>();
    private CellState[][] cellStates = new CellState[0][0];
    private Button[][] cellButtons = new Button[0][0];
    private ZoneOption activeZone;
    private StructuralTool activeStructural;
    private boolean eraseMode;
    private final VenueGridDragPainter gridDragPainter = new VenueGridDragPainter(this::paint);

    private EventMapDTO.LayoutInfo savedLayout;
    private EventStatus status = EventStatus.DRAFT;

    public VenueLayoutEditorComponent() {
        setPadding(false);
        setSpacing(true);

        add(new H4("Hall layout"));

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

        add(new Span("Repaint the hall: add/remove zones, build a grid, paint seats/GA, then Save layout. This rebuilds the event's tickets to match."));
        add(gridControls, zoneForm, new Span("Available Zones:"), zonePaletteList, tools, grid);
    }

    public void setEventMap(EventMapDTO map) {
        zones.clear();
        savedLayout = null;
        if (map == null) {
            this.status = EventStatus.DRAFT;
            return;
        }
        this.status = map.status();
        colorIndex = 0;
        for (EventMapDTO.ZoneInfo zone : map.zones()) {
            boolean ga = zone.type() == ZoneType.GENERAL_ADMISSION;
            int capacity = zone.maxCapacity() == null ? 0 : zone.maxCapacity();
            zones.add(new ZoneOption(zone.id(), zone.name(), ga, zone.pricePerTicket(), capacity,
                    ZONE_COLORS[colorIndex % ZONE_COLORS.length]));
            colorIndex++;
        }
        savedLayout = map.layout();
        prefillGrid();
        refreshZonePalette();
        
        boolean editable = status == EventStatus.DRAFT;
        setEnabled(editable);
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
            zonePaletteList.add(new Span("No zones assigned to this event yet."));
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
        UiMessages.info("Grid " + rows + "×" + cols + " ready. Select a zone or tool and click or drag across cells.");
    }

    private void rebuildGrid(int rows, int cols) {
        cellStates = new CellState[rows][cols];
        cellButtons = new Button[rows][cols];
        grid.removeAll();
        gridDragPainter.reset();
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
                gridDragPainter.wireCell(cell, rr, cc);
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

    public record EditorResult(
            int rows,
            int cols,
            List<CreateEventRequest.ZoneSpec> zoneSpecs,
            Map<String, String> sectionToZone,
            List<DefineVenueRequest.CellSpec> cellSpecs
    ) {}

    public EditorResult buildResult() {
        if (cellStates.length == 0) {
            throw new IllegalStateException("Build a grid first.");
        }

        List<CreateEventRequest.ZoneSpec> zoneSpecs = new ArrayList<>();
        Map<String, String> sectionToZone = new LinkedHashMap<>();
        for (ZoneOption zone : zones) {
            if (zone.ga) {
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
            throw new IllegalStateException("Paint at least one zone cell on the grid.");
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

        return new EditorResult(cellStates.length, cellStates[0].length, zoneSpecs, sectionToZone, cellSpecs);
    }

    private static int valueOr(IntegerField field, int fallback) {
        Integer v = field.getValue();
        return v == null ? fallback : v;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String rowLetter(int r) {
        StringBuilder sb = new StringBuilder();
        int v = r;
        do {
            sb.insert(0, (char) ('A' + (v % 26)));
            v = (v / 26) - 1;
        } while (v >= 0);
        return sb.toString();
    }
}
