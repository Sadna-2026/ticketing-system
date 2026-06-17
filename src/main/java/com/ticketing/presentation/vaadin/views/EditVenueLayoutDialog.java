package com.ticketing.presentation.vaadin.views;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.LayoutCellType;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventMapResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Edit the layout of an existing event. This dialog exposes ONLY the layout grid
 * and visual components. It is used by managers with the MAP_DEFINITION permission
 * who cannot edit event details, zones, or policies.
 */
public class EditVenueLayoutDialog extends Dialog {

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

    private record CellState(ZoneOption zone, StructuralTool structural, String label, UUID seatId) {
        boolean isZone() { return zone != null; }
        boolean isStructural() { return structural != null; }
    }

    private final CompanyPresenter presenter;
    private final String companyName;
    private UUID eventId;
    private EventStatus status;

    // Event selection
    private final ComboBox<EventSummaryDTO> eventPicker = new ComboBox<>("Event to manage");
    private final VerticalLayout body = new VerticalLayout();

    // Layout editor
    private final IntegerField rowsField = new IntegerField("Rows");
    private final IntegerField colsField = new IntegerField("Columns");
    private final TextField cellLabel = new TextField("Label (stage/object)");
    private final Div grid = new Div();
    private final VerticalLayout zonePaletteList = new VerticalLayout();
    private final Span layoutStatus = new Span();
    
    private int colorIndex;

    // State
    private final List<ZoneOption> zones = new ArrayList<>();
    private CellState[][] cellStates = new CellState[0][0];
    private Button[][] cellButtons = new Button[0][0];
    private ZoneOption activeZone;
    private StructuralTool activeStructural;
    private boolean eraseMode;

    public EditVenueLayoutDialog(CompanyPresenter presenter, String companyName) {
        this.presenter = presenter;
        this.companyName = companyName;

        setHeaderTitle("Edit venue layout — " + companyName);
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
        body.add(new Span("Select an event to edit its hall layout."));
    }

    private void loadSelectedEvent(EventSummaryDTO summary) {
        if (summary == null) {
            eventId = null;
            showHint();
            return;
        }
        eventId = summary.id();
        status = summary.status();
        loadEventMap();
        body.removeAll();
        body.add(buildLayoutSection());
    }

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

        layoutSection.add(new Span("Repaint the hall grid, then Save layout. Note: changing rows and columns clears the grid."));

        rowsField.setMin(1);
        colsField.setMin(1);

        Button buildGridBtn = new Button("Build grid", e -> buildGrid());
        buildGridBtn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        HorizontalLayout gridControls = new HorizontalLayout(rowsField, colsField, buildGridBtn);
        gridControls.setAlignItems(FlexComponent.Alignment.BASELINE);

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

        layoutSection.add(gridControls, new Span("Available Zones"), zonePaletteList, tools, grid, layoutActions, layoutStatus);

        prefillGrid();
        refreshZonePalette();
        return layoutSection;
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
            HorizontalLayout row = new HorizontalLayout(swatch, label, select);
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
            case STAGE -> new CellState(null, StructuralTool.STAGE, cell.label(), null);
            case OBJECT -> new CellState(null, StructuralTool.OBJECT, cell.label(), null);
            case BLOCKED -> new CellState(null, StructuralTool.BLOCKED, null, null);
            case GENERAL_ADMISSION -> {
                ZoneOption zone = zoneByLabelOrId(cell.label(), cell.zoneId(), true);
                yield zone == null ? null : new CellState(zone, null, null, null);
            }
            case SEAT -> {
                ZoneOption zone = cell.zoneId() != null
                        ? zoneById(cell.zoneId())
                        : firstSeating;
                yield zone == null ? null : new CellState(zone, null, null, cell.seatId());
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
            cellStates[r][c] = new CellState(null, activeStructural, blankToNull(cellLabel.getValue()), null);
            repaintCell(r, c);
            return;
        }
        if (activeZone != null) {
            UUID existingSeatId = null;
            CellState oldState = cellStates[r][c];
            if (oldState != null && oldState.zone() == activeZone) {
                existingSeatId = oldState.seatId();
            }
            cellStates[r][c] = new CellState(activeZone, null, null, existingSeatId);
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

        List<com.ticketing.domain.event.LayoutCell> domainCells = new ArrayList<>();
        for (int r = 0; r < cellStates.length; r++) {
            for (int c = 0; c < cellStates[r].length; c++) {
                CellState s = cellStates[r][c];
                if (s == null) {
                    continue;
                }
                if (s.isZone()) {
                    if (s.zone().ga) {
                        domainCells.add(com.ticketing.domain.event.LayoutCell.ga(r, c, s.zone().id, s.zone().name));
                    } else {
                        domainCells.add(com.ticketing.domain.event.LayoutCell.seat(r, c, s.zone().id, s.seatId()));
                    }
                } else {
                    com.ticketing.domain.event.LayoutCellType type = switch (s.structural()) {
                        case BLOCKED -> com.ticketing.domain.event.LayoutCellType.BLOCKED;
                        case STAGE -> com.ticketing.domain.event.LayoutCellType.STAGE;
                        case OBJECT -> com.ticketing.domain.event.LayoutCellType.OBJECT;
                    };
                    domainCells.add(new com.ticketing.domain.event.LayoutCell(r, c, type, s.label(), null, null));
                }
            }
        }

        com.ticketing.domain.event.VenueLayout layout = new com.ticketing.domain.event.VenueLayout(cellStates.length, cellStates[0].length, domainCells);
        ActionResult result = presenter.setEventLayout(eventId, layout);
        layoutStatus.setText(result.message());
        showResult(result.success(), result.message());
        if (result.success()) {
            loadEventMap();
            body.removeAll();
            body.add(buildLayoutSection());
        }
    }

    private void validateLayout() {
        ActionResult result = presenter.validateEventLayout(eventId);
        layoutStatus.setText(result.message());
        showResult(result.success(), result.message());
    }

    // ── Loading ──

    private EventMapDTO.LayoutInfo savedLayout;

    private void loadEventMap() {
        EventMapResult result = presenter.loadEventMapForManagement(eventId);
        zones.clear();
        savedLayout = null;
        if (result == null || !result.success() || result.eventMap() == null) {
            return;
        }
        EventMapDTO map = result.eventMap();
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
}
