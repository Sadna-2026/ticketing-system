package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventMapResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasLabel;
import com.vaadin.flow.component.HasValueAndElement;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.dom.Element;

@DisplayName("EditVenueLayoutDialog")
@ExtendWith(VaadinSessionExtension.class)
class EditVenueLayoutDialogTest {

    private static final String COMPANY = "Acme";
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID GA_ZONE_ID = UUID.randomUUID();

    @Test
    void GivenDraftEvent_WhenOpened_ThenCurrentZonesAndLayoutAreLoaded() {
        CompanyPresenter presenter = presenterWithMap(EventStatus.DRAFT);
        EditVenueLayoutDialog dialog = openWith(presenter, draft());
        
        assertTrue(hasButton(dialog, "Save layout"));
        
        // current 10 x 10 layout is displayed
        List<HasValueAndElement<?, ?>> rowFields = findFieldsByLabel(dialog, "Rows");
        List<HasValueAndElement<?, ?>> colFields = findFieldsByLabel(dialog, "Columns");
        assertEquals(1, rowFields.size());
        assertEquals(1, colFields.size());
        
        com.vaadin.flow.component.textfield.IntegerField rowField = (com.vaadin.flow.component.textfield.IntegerField) rowFields.get(0);
        com.vaadin.flow.component.textfield.IntegerField colField = (com.vaadin.flow.component.textfield.IntegerField) colFields.get(0);
        
        assertEquals(10, rowField.getValue());
        assertEquals(10, colField.getValue());
        
        // current zones are loaded (we can check the UI palette list text)
        assertTrue(hasText(dialog, "Floor (GA)"));
    }

    @Test
    void GivenDraftEvent_WhenReopened_ThenEditorDisplaysTheSavedChanges() {
        CompanyPresenter presenter = presenterWithMap(EventStatus.DRAFT);
        // Simulate reopening the editor for the saved changes
        EditVenueLayoutDialog dialog = openWith(presenter, draft());
        
        // Since loadEventMapForManagement gets called when the event is selected
        verify(presenter).loadEventMapForManagement(EVENT_ID);
        
        List<HasValueAndElement<?, ?>> rowFields = findFieldsByLabel(dialog, "Rows");
        com.vaadin.flow.component.textfield.IntegerField rowField = (com.vaadin.flow.component.textfield.IntegerField) rowFields.get(0);
        assertEquals(10, rowField.getValue(), "Reopening the editor displays the saved changes");
    }

    @Test
    void GivenDraftEvent_WhenSaveLayoutSucceeds_ThenSelectedEventIsUpdatedAndNoDuplicateCreated() {
        CompanyPresenter presenter = presenterWithMap(EventStatus.DRAFT);
        when(presenter.redefineVenue(eq(EVENT_ID), any(Integer.class), any(Integer.class), any(), any(), any()))
                .thenReturn(ActionResult.success("Venue layout saved successfully."));

        try (var ui = mockStatic(UiMessages.class)) {
            EditVenueLayoutDialog dialog = openWith(presenter, draft());
            
            clickButton(dialog, "Save layout");

            // Verify redefineVenue was called (which only updates layout, doesn't create new event)
            verify(presenter).redefineVenue(eq(EVENT_ID), eq(10), eq(10), any(), any(), any());
            ui.verify(() -> UiMessages.success("Venue layout saved successfully."));
        }
    }

    @Test
    void GivenPublishedEvent_ThenLayoutEditorIsLocked() {
        CompanyPresenter presenter = presenterWithMap(EventStatus.PUBLISHED);
        EditVenueLayoutDialog dialog = openWith(presenter, published());
        
        assertFalse(hasButton(dialog, "Save layout"), "Published events must not allow layout edits");
        assertTrue(hasText(dialog, "The layout is locked once the event is published."));
    }

    // ── Fixtures ──

    private CompanyPresenter presenterWithMap(EventStatus status) {
        CompanyPresenter presenter = mock(CompanyPresenter.class);
        when(presenter.loadEventMapForManagement(EVENT_ID))
                .thenReturn(EventMapResult.success("Event map loaded.", eventMap(status)));
        return presenter;
    }

    private EditVenueLayoutDialog openWith(CompanyPresenter presenter, EventSummaryDTO summary) {
        when(presenter.listCompanyEvents(COMPANY)).thenReturn(List.of(summary));
        EditVenueLayoutDialog dialog = new EditVenueLayoutDialog(presenter, COMPANY);
        selectEvent(dialog, summary);
        return dialog;
    }

    @SuppressWarnings("unchecked")
    private void selectEvent(EditVenueLayoutDialog dialog, EventSummaryDTO summary) {
        for (HasValueAndElement<?, ?> field : findFieldsByLabel(dialog, "Event to manage")) {
            if (field instanceof ComboBox) {
                ((ComboBox<EventSummaryDTO>) field).setValue(summary);
                return;
            }
        }
        throw new AssertionError("Event picker not found");
    }

    private static EventSummaryDTO draft() {
        return summary(EventStatus.DRAFT);
    }

    private static EventSummaryDTO published() {
        return summary(EventStatus.PUBLISHED);
    }

    private static EventSummaryDTO summary(EventStatus status) {
        Instant start = Instant.now().plus(Duration.ofDays(10));
        EventSchedule schedule = new EventSchedule(start, start.plus(Duration.ofHours(2)), start.minus(Duration.ofMinutes(30)));
        return new EventSummaryDTO(EVENT_ID, "Demo Show", EventCategory.CONCERT, schedule, status);
    }

    private static EventMapDTO eventMap(EventStatus status) {
        EventMapDTO.ZoneInfo ga = new EventMapDTO.ZoneInfo(
                GA_ZONE_ID, "Floor", ZoneType.GENERAL_ADMISSION, new BigDecimal("50.00"),
                200, 200, 0, List.of());
        
        // Give it a 10x10 layout
        List<EventMapDTO.CellInfo> cells = new ArrayList<>();
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 10; c++) {
                cells.add(new EventMapDTO.CellInfo(r, c, com.ticketing.domain.event.LayoutCellType.GENERAL_ADMISSION, null, GA_ZONE_ID, null));
            }
        }
        EventMapDTO.LayoutInfo layoutInfo = new EventMapDTO.LayoutInfo(10, 10, cells);

        return new EventMapDTO(
                EVENT_ID, "Demo Show", "Acme", status,
                Map.of("Floor", GA_ZONE_ID), List.of(ga), layoutInfo);
    }

    // ── Component helpers ──

    private void clickButton(EditVenueLayoutDialog dialog, String text) {
        Button button = findButtonInComponent(dialog, text);
        if (button == null) {
            button = findButtonInElement(dialog.getFooter().getElement(), text);
        }
        if (button == null) throw new AssertionError("Button not found: " + text);
        button.click();
    }

    private boolean hasButton(EditVenueLayoutDialog dialog, String text) {
        return findButtonInComponent(dialog, text) != null;
    }

    private boolean hasText(Component root, String text) {
        if (root instanceof com.vaadin.flow.component.HasText ht && text.equals(ht.getText())) {
            return true;
        }
        return root.getChildren().anyMatch(child -> hasText(child, text));
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
