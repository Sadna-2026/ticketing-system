package com.ticketing.presentation.vaadin.views;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketing.application.CreateEventRequest;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.LayoutCell;
import com.ticketing.domain.event.LayoutCellType;
import com.ticketing.domain.event.VenueLayout;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventActionResult;
import com.ticketing.presentation.vaadin.util.RequiredFields;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;

/**
 * FIX-V2-25 — visual hall / event-map designer.
 *
 * <p>Lets an event manager paint a grid: exact seats, general-admission areas (with
 * capacity), blocked areas, stages and other objects, mixed in one event. "Save draft"
 * creates the event (zones derived from the painted seat/GA cells) and stores the visual
 * {@link VenueLayout}; "Validate" checks it; "Publish" makes it live. The buyer-facing
 * event map renders the saved layout grid.
 *
 * <p>Pure presentation: all rules go through {@link CompanyPresenter} → application services.
 * Every action surfaces its outcome through the shared {@link UiMessages} helper (UX-7).
 */
public class VenueDesignerDialog extends Dialog {

    /** What the currently-selected paint tool places into a clicked cell. */
    private enum CellMode {
        SEAT("Seat", "S", "#1976d2", LayoutCellType.SEAT),
        GA("GA area", "G", "#2e7d32", LayoutCellType.GENERAL_ADMISSION),
        BLOCKED("Blocked", "X", "#616161", LayoutCellType.BLOCKED),
        STAGE("Stage", "ST", "#6a1b9a", LayoutCellType.STAGE),
        OBJECT("Object", "O", "#ef6c00", LayoutCellType.OBJECT),
        ERASE("Erase", "", "transparent", null);

        final String label;
        final String glyph;
        final String color;
        final LayoutCellType cellType;

        CellMode(String label, String glyph, String color, LayoutCellType cellType) {
            this.label = label;
            this.glyph = glyph;
            this.color = color;
            this.cellType = cellType;
        }
    }

    private final CompanyPresenter presenter;
    private final String companyName;

    private final TextField eventName = new TextField("Event name");
    private final TextField description = new TextField("Description");
    private final ComboBox<EventCategory> category = new ComboBox<>("Category");
    private final DateTimePicker startTime = new DateTimePicker("Start");
    private final IntegerField lockMinutes = new IntegerField("Lock minutes");

    private final IntegerField rowsField = new IntegerField("Rows");
    private final IntegerField colsField = new IntegerField("Columns");
    private final RadioButtonGroup<CellMode> mode = new RadioButtonGroup<>();
    private final BigDecimalField seatPrice = new BigDecimalField("Seat price");
    private final BigDecimalField gaPrice = new BigDecimalField("GA price");
    private final IntegerField gaCapacity = new IntegerField("GA capacity");
    private final TextField cellLabel = new TextField("Label (stage/object)");

    private final Div grid = new Div();
    private final Span status = new Span("Build a grid, paint cells, then Save draft.");
    private final Button saveDraft = new Button("Save draft", e -> saveDraft());
    private final Button validate = new Button("Validate", e -> validateLayout());
    private final Button publish = new Button("Publish", e -> publish());

    private CellMode[][] cells = new CellMode[0][0];
    private Button[][] cellButtons = new Button[0][0];
    private UUID eventId; // set after a successful save

    public VenueDesignerDialog(CompanyPresenter presenter, String companyName) {
        this.presenter = presenter;
        this.companyName = companyName;

        setHeaderTitle("Hall designer — " + companyName);
        setWidth("900px");

        category.setItems(EventCategory.values());
        category.setItemLabelGenerator(EventCategory::name);
        category.setValue(EventCategory.CONCERT);
        lockMinutes.setMin(1);
        lockMinutes.setValue(15);
        rowsField.setMin(1);
        rowsField.setValue(5);
        colsField.setMin(1);
        colsField.setValue(8);
        seatPrice.setValue(new BigDecimal("100.00"));
        gaPrice.setValue(new BigDecimal("50.00"));
        gaCapacity.setMin(1);
        gaCapacity.setValue(100);

        mode.setLabel("Tool");
        mode.setItems(CellMode.values());
        mode.setItemLabelGenerator(m -> m.label);
        mode.setValue(CellMode.SEAT);

        // Inline validation for the inputs that have no sensible default (UX-7).
        RequiredFields.markRequired(eventName, "Event name is required.");
        RequiredFields.markRequired(startTime, "Start time is required.");

        Button buildGrid = new Button("Build grid", e -> buildGrid());
        grid.getStyle().set("overflow", "auto").set("max-height", "320px").set("padding", "4px");

        validate.setEnabled(false);
        publish.setEnabled(false);

        VerticalLayout content = new VerticalLayout(
                new Span("1) Event details"),
                new HorizontalLayout(eventName, category, startTime, lockMinutes),
                description,
                new Span("2) Grid"),
                new HorizontalLayout(rowsField, colsField, buildGrid),
                new Span("3) Paint"),
                new HorizontalLayout(mode),
                new HorizontalLayout(seatPrice, gaPrice, gaCapacity, cellLabel),
                grid,
                status);
        content.setPadding(false);
        content.setSpacing(true);
        add(content);

        Button close = new Button("Close", e -> close());
        getFooter().add(close, saveDraft, validate, publish);

        buildGrid();
    }

    private void buildGrid() {
        int rows = valueOr(rowsField, 5);
        int cols = valueOr(colsField, 8);
        cells = new CellMode[rows][cols];
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
                        .set("background", "var(--lumo-contrast-5pct)");
                final int rr = r;
                final int cc = c;
                cell.addClickListener(e -> paint(rr, cc));
                cellButtons[r][c] = cell;
                grid.add(cell);
            }
        }
        // Reset save state: a fresh grid is a new draft.
        eventId = null;
        validate.setEnabled(false);
        publish.setEnabled(false);
        String ready = "Grid " + rows + "x" + cols + " ready. Pick a tool and click cells.";
        status.setText(ready);
        UiMessages.info(ready);
    }

    private void paint(int r, int c) {
        CellMode selected = mode.getValue();
        Button cell = cellButtons[r][c];
        if (selected == CellMode.ERASE) {
            cells[r][c] = null;
            cell.setText("");
            cell.getStyle().set("background", "var(--lumo-contrast-5pct)").set("color", "inherit");
            return;
        }
        cells[r][c] = selected;
        cell.setText(selected.glyph);
        cell.getStyle().set("background", selected.color).set("color", "white");
    }

    private void saveDraft() {
        if (cells.length == 0) {
            UiMessages.error("Build a grid first.");
            return;
        }
        List<CreateEventRequest.SeatSpec> seatSpecs = new ArrayList<>();
        boolean hasGa = false;
        List<LayoutCell> layoutCells = new ArrayList<>();
        for (int r = 0; r < cells.length; r++) {
            for (int c = 0; c < cells[r].length; c++) {
                CellMode m = cells[r][c];
                if (m == null) {
                    continue;
                }
                switch (m) {
                    case SEAT -> {
                        seatSpecs.add(new CreateEventRequest.SeatSpec(rowLetter(r), String.valueOf(c + 1)));
                        layoutCells.add(new LayoutCell(r, c, LayoutCellType.SEAT, null, null, null));
                    }
                    case GA -> {
                        hasGa = true;
                        layoutCells.add(new LayoutCell(r, c, LayoutCellType.GENERAL_ADMISSION, "Floor", null, null));
                    }
                    case BLOCKED -> layoutCells.add(LayoutCell.blocked(r, c));
                    case STAGE -> layoutCells.add(LayoutCell.stage(r, c, blankToNull(cellLabel.getValue())));
                    case OBJECT -> layoutCells.add(LayoutCell.object(r, c, blankToNull(cellLabel.getValue())));
                    case ERASE -> { /* not stored */ }
                }
            }
        }

        List<CreateEventRequest.ZoneSpec> zones = new ArrayList<>();
        Map<String, String> sectionToZone = new LinkedHashMap<>();
        if (!seatSpecs.isEmpty()) {
            zones.add(new CreateEventRequest.AssignedZoneSpec("Reserved Seating", seatPrice.getValue(), seatSpecs));
            sectionToZone.put("Reserved Seating", "Reserved Seating");
        }
        if (hasGa) {
            zones.add(new CreateEventRequest.GAZoneSpec("General Admission", gaPrice.getValue(), valueOr(gaCapacity, 100)));
            sectionToZone.put("General Admission", "General Admission");
        }
        if (zones.isEmpty()) {
            UiMessages.error("Paint at least one seat or general-admission cell.");
            return;
        }

        Instant start = toInstant(startTime.getValue());
        if (start == null) {
            UiMessages.error("Pick a start time.");
            return;
        }
        Instant end = start.plus(Duration.ofHours(3));
        Instant doors = start.minus(Duration.ofHours(1));

        EventActionResult created = presenter.createEventWithZones(
                companyName, eventName.getValue(), description.getValue(), category.getValue(),
                start, end, doors, lockMinutes.getValue(), zones, sectionToZone);
        if (!created.success() || created.eventId() == null) {
            status.setText("Save failed: " + created.message());
            UiMessages.error(created.message());
            return;
        }

        VenueLayout layout = new VenueLayout(cells.length, cells[0].length, layoutCells);
        ActionResult layoutResult = presenter.setEventLayout(created.eventId(), layout);
        if (!layoutResult.success()) {
            status.setText("Event created, but layout save failed: " + layoutResult.message());
            UiMessages.error(layoutResult.message());
            return;
        }

        eventId = created.eventId();
        validate.setEnabled(true);
        publish.setEnabled(true);
        status.setText("Draft saved (" + seatSpecs.size() + " seats, GA=" + hasGa
                + ", " + layoutCells.size() + " cells). Validate, then Publish.");
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

    /** Surfaces an action outcome through the shared feedback helper (success or specific failure). */
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
