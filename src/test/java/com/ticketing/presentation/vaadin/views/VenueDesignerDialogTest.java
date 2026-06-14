package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.presentation.vaadin.presenters.CompanyPresenter;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventActionResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasLabel;
import com.vaadin.flow.component.HasValueAndElement;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
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
            assertRequired(dialog, "Start");
            assertOptional(dialog, "Description");
            assertOptional(dialog, "Category");
        }
    }

    @Test
    void GivenNothingPainted_WhenSaveDraft_ThenShowsSpecificError() {
        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            VenueDesignerDialog dialog = new VenueDesignerDialog(mock(CompanyPresenter.class), COMPANY);

            clickButton(dialog, "Save draft");

            uiMessagesMock.verify(() -> UiMessages.error("Paint at least one seat or general-admission cell."));
        }
    }

    @Test
    void GivenSeatPaintedAndStartSet_WhenSaveDraft_ThenShowsSuccessMessage() {
        CompanyPresenter presenter = mock(CompanyPresenter.class);
        when(presenter.createEventWithZones(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EventActionResult(true, "Event created.", UUID.randomUUID(), null));
        when(presenter.setEventLayout(any(), any())).thenReturn(new ActionResult(true, "Layout saved."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            VenueDesignerDialog dialog = new VenueDesignerDialog(presenter, COMPANY);
            setStartTime(dialog, LocalDateTime.now().plusDays(1));
            clickFirstGridCell(dialog); // default tool is SEAT → paints one seat
            clickButton(dialog, "Save draft");

            uiMessagesMock.verify(() -> UiMessages.success("Draft saved."));
        }
    }

    @Test
    void GivenSavedDraft_WhenValidateFailsThenPublishSucceeds_ThenEachOutcomeIsSurfaced() {
        CompanyPresenter presenter = mock(CompanyPresenter.class);
        when(presenter.createEventWithZones(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EventActionResult(true, "Event created.", UUID.randomUUID(), null));
        when(presenter.setEventLayout(any(), any())).thenReturn(new ActionResult(true, "Layout saved."));
        when(presenter.validateEventLayout(any()))
                .thenReturn(new ActionResult(false, "Layout invalid: a seat overlaps the stage."));
        when(presenter.publishEvent(any())).thenReturn(new ActionResult(true, "Event published."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            VenueDesignerDialog dialog = new VenueDesignerDialog(presenter, COMPANY);
            setStartTime(dialog, LocalDateTime.now().plusDays(1));
            clickFirstGridCell(dialog);
            clickButton(dialog, "Save draft");

            clickButton(dialog, "Validate");
            clickButton(dialog, "Publish");

            uiMessagesMock.verify(() -> UiMessages.error("Layout invalid: a seat overlaps the stage."));
            uiMessagesMock.verify(() -> UiMessages.success("Event published."));
        }
    }

    // ---- helpers ----

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

    private void setStartTime(Component root, LocalDateTime value) {
        DateTimePicker picker = findComponent(root, DateTimePicker.class);
        if (picker == null) {
            throw new AssertionError("Start time picker not found");
        }
        picker.setValue(value);
    }

    /** Clicks the first grid cell — a Button with blank text inside the painting grid. */
    private void clickFirstGridCell(Component root) {
        Button cell = findBlankButton(root);
        if (cell == null) {
            throw new AssertionError("No grid cell button found");
        }
        cell.click();
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
        // Footer action buttons (Save draft/Validate/Publish) live under the footer slot
        // element, not in the dialog's component children, so search both.
        Button button = findButtonInComponent(dialog, text);
        if (button == null) {
            button = findButtonInElement(dialog.getFooter().getElement(), text);
        }
        if (button == null) {
            throw new AssertionError("Button not found: " + text);
        }
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
        if (match != null) {
            return match;
        }
        return element.getChildren()
                .map(child -> findButtonInElement(child, text))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private <T extends Component> T findComponent(Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
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
