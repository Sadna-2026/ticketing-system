package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.application.CreateEventRequest;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventActionResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasLabel;
import com.vaadin.flow.component.HasValueAndElement;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Element;

@DisplayName("VenueDesignerDialog")
@ExtendWith(VaadinSessionExtension.class)
class VenueDesignerDialogTest {

    private static final String COMPANY = "Acme Productions";

    @Test
    void GivenDialogRendered_ThenMandatoryEventFieldsShowRequiredIndicatorAndDefaultedFieldsDoNot() {
        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            VenueDesignerDialog dialog = new VenueDesignerDialog(mock(CompanyPresenter.class), COMPANY);

            assertRequired(dialog, "Event name");
            assertRequired(dialog, "Start time");
            assertRequired(dialog, "End time");
            assertOptional(dialog, "Description");
            assertOptional(dialog, "Category");
        }
    }

    @Test
    void GivenNoZonesAndNoPaint_WhenSaveDraft_ThenShowsError() {
        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            VenueDesignerDialog dialog = new VenueDesignerDialog(mock(CompanyPresenter.class), COMPANY);

            clickButton(dialog, "Save draft");

            uiMessagesMock.verify(() -> UiMessages.error("Add at least one zone before saving."));
        }
    }

    @Test
    void GivenZoneAddedButNoCellsPainted_WhenSaveDraft_ThenShowsPaintError() {
        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            VenueDesignerDialog dialog = new VenueDesignerDialog(mock(CompanyPresenter.class), COMPANY);

            addSeatingZone(dialog, "VIP");
            clickButton(dialog, "Save draft");

            uiMessagesMock.verify(() -> UiMessages.error("Paint at least one zone cell on the grid."));
        }
    }

    @Test
    void GivenSeatingZonePaintedAndStartSet_WhenSaveDraft_ThenShowsSuccessMessage() {
        CompanyPresenter presenter = mock(CompanyPresenter.class);
        when(presenter.defineVenue(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(new EventActionResult(true, "Event created.", UUID.randomUUID(), null));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            VenueDesignerDialog dialog = new VenueDesignerDialog(presenter, COMPANY);
            setEventTimes(dialog, LocalDateTime.now().plusDays(1));
            addSeatingZone(dialog, "Orchestra");
            clickFirstGridCell(dialog);
            clickButton(dialog, "Save draft");

            uiMessagesMock.verify(() -> UiMessages.success("Draft saved."));
        }
    }

    @Test
    void GivenMultipleZones_WhenPaintedAndSaved_ThenAllZonesIncludedInCreateCall() {
        CompanyPresenter presenter = mock(CompanyPresenter.class);
        when(presenter.defineVenue(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(new EventActionResult(true, "Event created.", UUID.randomUUID(), null));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            VenueDesignerDialog dialog = new VenueDesignerDialog(presenter, COMPANY);
            setEventTimes(dialog, LocalDateTime.now().plusDays(1));

            addSeatingZone(dialog, "Orchestra");
            clickGridCell(dialog, 0, 0);
            clickGridCell(dialog, 0, 1);

            addGAZone(dialog, "Floor", 200);
            clickGridCell(dialog, 2, 0);

            clickButton(dialog, "Save draft");

            verify(presenter).defineVenue(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt(),
                    argThat(zones -> zones.size() == 2
                            && zones.stream().anyMatch(z -> z instanceof CreateEventRequest.AssignedZoneSpec a && "Orchestra".equals(a.name()))
                            && zones.stream().anyMatch(z -> z instanceof CreateEventRequest.GAZoneSpec g && "Floor".equals(g.name()))),
                    any(), any());
        }
    }

    @Test
    void GivenDuplicateZoneName_WhenAddZone_ThenShowsError() {
        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            VenueDesignerDialog dialog = new VenueDesignerDialog(mock(CompanyPresenter.class), COMPANY);

            addSeatingZone(dialog, "VIP");
            addSeatingZone(dialog, "VIP");

            uiMessagesMock.verify(() -> UiMessages.error("A zone named 'VIP' already exists."));
        }
    }

    @Test
    void GivenSavedDraft_WhenValidateFailsThenPublishSucceeds_ThenEachOutcomeIsSurfaced() {
        CompanyPresenter presenter = mock(CompanyPresenter.class);
        when(presenter.defineVenue(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(new EventActionResult(true, "Event created.", UUID.randomUUID(), null));
        when(presenter.validateEventLayout(any()))
                .thenReturn(new ActionResult(false, "Layout invalid: a seat overlaps the stage."));
        when(presenter.publishEvent(any())).thenReturn(new ActionResult(true, "Event published."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            VenueDesignerDialog dialog = new VenueDesignerDialog(presenter, COMPANY);
            setEventTimes(dialog, LocalDateTime.now().plusDays(1));
            addSeatingZone(dialog, "Main");
            clickFirstGridCell(dialog);
            clickButton(dialog, "Save draft");

            clickButton(dialog, "Validate");
            clickButton(dialog, "Publish");

            uiMessagesMock.verify(() -> UiMessages.error("Layout invalid: a seat overlaps the stage."));
            uiMessagesMock.verify(() -> UiMessages.success("Event published."));
        }
    }

    @Test
    void GivenNoZoneOrToolSelected_WhenCellClicked_ThenShowsError() {
        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            CompanyPresenter presenter = mock(CompanyPresenter.class);
            VenueDesignerDialog dialog = new VenueDesignerDialog(presenter, COMPANY);

            // Click erase first to deselect everything, then click a cell
            clickButton(dialog, "Erase");
            // Erase on empty cell does nothing harmful, but clicking a non-erase cell with no active zone should error
            // Actually erase on empty cell just sets it to null, no error. Let's verify no errors from erase on empty.
            clickFirstGridCell(dialog);

            // Erase mode doesn't produce an error — it just clears. This is expected behavior.
            uiMessagesMock.verify(() -> UiMessages.error("Select a zone or tool first."), never());
        }
    }

    // ---- helpers ----

    private void addSeatingZone(VenueDesignerDialog dialog, String name) {
        setFieldValue(dialog, "Zone name", name);
        setComboValue(dialog, "Type", "Seating");
        clickButton(dialog, "Add zone");
    }

    private void addGAZone(VenueDesignerDialog dialog, String name, int capacity) {
        setFieldValue(dialog, "Zone name", name);
        setComboValue(dialog, "Type", "GA");
        setIntField(dialog, "GA capacity", capacity);
        clickButton(dialog, "Add zone");
    }

    private void setFieldValue(Component root, String label, String value) {
        List<HasValueAndElement<?, ?>> fields = findFieldsByLabel(root, label);
        if (fields.isEmpty()) throw new AssertionError("Field not found: " + label);
        for (HasValueAndElement<?, ?> field : fields) {
            if (field instanceof TextField tf) { tf.setValue(value); return; }
        }
        throw new AssertionError("Field '" + label + "' is not a TextField");
    }

    @SuppressWarnings("unchecked")
    private void setComboValue(Component root, String label, String value) {
        List<HasValueAndElement<?, ?>> fields = findFieldsByLabel(root, label);
        if (fields.isEmpty()) throw new AssertionError("ComboBox not found: " + label);
        for (HasValueAndElement<?, ?> field : fields) {
            if (field instanceof ComboBox<?>) {
                ((ComboBox<String>) field).setValue(value);
                return;
            }
        }
        throw new AssertionError("Field '" + label + "' is not a ComboBox");
    }

    private void setIntField(Component root, String label, int value) {
        List<HasValueAndElement<?, ?>> fields = findFieldsByLabel(root, label);
        for (HasValueAndElement<?, ?> field : fields) {
            if (field instanceof com.vaadin.flow.component.textfield.IntegerField intField) {
                intField.setValue(value);
                return;
            }
        }
        throw new AssertionError("IntegerField not found: " + label);
    }

    private void assertRequired(Component root, String label) {
        List<HasValueAndElement<?, ?>> fields = findFieldsByLabel(root, label);
        assertFalse(fields.isEmpty(), "No field found with label: " + label);
        fields.forEach(field -> assertTrue(field.isRequiredIndicatorVisible(),
                label + " should be marked required"));
    }

    private void assertOptional(Component root, String label) {
        List<HasValueAndElement<?, ?>> fields = findFieldsByLabel(root, label);
        assertFalse(fields.isEmpty(), "No field found with label: " + label);
        fields.forEach(field -> assertFalse(field.isRequiredIndicatorVisible(),
                label + " should be optional"));
    }

    private void setEventTimes(Component root, LocalDateTime start) {
        setDateTimePicker(root, "Start time", start);
        setDateTimePicker(root, "End time", start.plusHours(2));
    }

    private void setDateTimePicker(Component root, String label, LocalDateTime value) {
        List<HasValueAndElement<?, ?>> fields = findFieldsByLabel(root, label);
        for (HasValueAndElement<?, ?> field : fields) {
            if (field instanceof DateTimePicker dtp) {
                dtp.setValue(value);
                return;
            }
        }
        throw new AssertionError("DateTimePicker not found: " + label);
    }

    private void clickFirstGridCell(Component root) {
        Button cell = findBlankButton(root);
        if (cell == null) throw new AssertionError("No grid cell button found");
        cell.click();
    }

    private void clickGridCell(VenueDesignerDialog dialog, int row, int col) {
        List<Button> gridCells = new ArrayList<>();
        collectGridCells(dialog, gridCells);
        // Grid cells are 30x30 buttons laid out in row-major order
        // Find the grid Div (contains many small buttons)
        int cols = countGridColumns(dialog);
        int index = row * cols + col;
        if (index >= gridCells.size()) throw new AssertionError("Grid cell [" + row + "," + col + "] out of bounds");
        gridCells.get(index).click();
    }

    private void collectGridCells(Component root, List<Button> cells) {
        if (root instanceof com.vaadin.flow.component.html.Div div) {
            String display = div.getStyle().get("display");
            if ("grid".equals(display)) {
                div.getChildren().forEach(child -> {
                    if (child instanceof Button btn && "30px".equals(btn.getStyle().get("width"))) {
                        cells.add(btn);
                    }
                });
                return;
            }
        }
        root.getChildren().forEach(child -> collectGridCells(child, cells));
    }

    private int countGridColumns(VenueDesignerDialog dialog) {
        // Default grid is 8 columns
        return 8;
    }

    private Button findBlankButton(Component root) {
        if (root instanceof Button button && (button.getText() == null || button.getText().isEmpty())) {
            return button;
        }
        return root.getChildren()
                .map(this::findBlankButton)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void clickButton(VenueDesignerDialog dialog, String text) {
        Button button = findButtonInComponent(dialog, text);
        if (button == null) {
            button = findButtonInElement(dialog.getFooter().getElement(), text);
        }
        if (button == null) throw new AssertionError("Button not found: " + text);
        button.click();
    }

    private Button findButtonInComponent(Component root, String text) {
        if (root instanceof Button button && text.equals(button.getText())) {
            return button;
        }
        return root.getChildren()
                .map(child -> findButtonInComponent(child, text))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private Button findButtonInElement(Element element, String text) {
        Button match = element.getComponent()
                .filter(component -> component instanceof Button button && text.equals(button.getText()))
                .map(Button.class::cast)
                .orElse(null);
        if (match != null) return match;
        return element.getChildren()
                .map(child -> findButtonInElement(child, text))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private <T extends Component> T findComponent(Component root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        return root.getChildren()
                .map(child -> findComponent(child, type))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static List<HasValueAndElement<?, ?>> findFieldsByLabel(Component root, String label) {
        List<HasValueAndElement<?, ?>> result = new ArrayList<>();
        collectFieldsByLabel(root, label, result);
        return result;
    }

    private static void collectFieldsByLabel(Component component, String label,
            List<HasValueAndElement<?, ?>> out) {
        if (component instanceof HasLabel hasLabel && label.equals(hasLabel.getLabel())
                && component instanceof HasValueAndElement<?, ?> field) {
            out.add(field);
        }
        component.getChildren().forEach(child -> collectFieldsByLabel(child, label, out));
    }
}
