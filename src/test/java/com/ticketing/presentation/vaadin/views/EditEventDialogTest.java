package com.ticketing.presentation.vaadin.views;

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
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventMapResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasLabel;
import com.vaadin.flow.component.HasValueAndElement;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Element;

@DisplayName("EditEventDialog")
@ExtendWith(VaadinSessionExtension.class)
class EditEventDialogTest {

    private static final String COMPANY = "Acme";
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID GA_ZONE_ID = UUID.randomUUID();

    // ── Event details ──

    @Test
    void GivenDraftEvent_WhenSaveDetailsSucceeds_ThenConfirmationIsShownAndPresenterCalled() {
        CompanyPresenter presenter = presenterWithMap(EventStatus.DRAFT);
        when(presenter.editEvent(eq(EVENT_ID), any(), any(), any(), any(), any(), any()))
                .thenReturn(EventActionResult.edited("Event details updated.", null));

        try (var ui = mockStatic(UiMessages.class)) {
            EditEventDialog dialog = openWith(presenter, draft());
            setTextField(dialog, "Event name", "Renamed Show");
            clickButton(dialog, "Save details");

            verify(presenter).editEvent(eq(EVENT_ID), eq("Renamed Show"), any(), any(), any(), any(), any());
            ui.verify(() -> UiMessages.success("Event details updated."));
        }
    }

    @Test
    void GivenDraftEvent_WhenSaveDetailsFails_ThenSpecificReasonIsShown() {
        CompanyPresenter presenter = presenterWithMap(EventStatus.DRAFT);
        when(presenter.editEvent(eq(EVENT_ID), any(), any(), any(), any(), any(), any()))
                .thenReturn(EventActionResult.failure("Cannot edit event with active reservations"));

        try (var ui = mockStatic(UiMessages.class)) {
            EditEventDialog dialog = openWith(presenter, draft());
            clickButton(dialog, "Save details");

            ui.verify(() -> UiMessages.error("Cannot edit event with active reservations"));
        }
    }

    // ── Zones ──

    @Test
    void GivenGaZone_WhenSetPrice_ThenPresenterCalledWithNewPriceAndConfirmed() {
        CompanyPresenter presenter = presenterWithMap(EventStatus.DRAFT);
        when(presenter.setZonePrice(eq(EVENT_ID), eq(GA_ZONE_ID), any()))
                .thenReturn(ActionResult.success("Zone price updated."));

        try (var ui = mockStatic(UiMessages.class)) {
            EditEventDialog dialog = openWith(presenter, draft());
            setBigDecimalField(dialog, "Price", new BigDecimal("99.00"));
            clickButton(dialog, "Set price");

            verify(presenter).setZonePrice(EVENT_ID, GA_ZONE_ID, new BigDecimal("99.00"));
            ui.verify(() -> UiMessages.success("Zone price updated."));
        }
    }

    @Test
    void GivenGaZone_WhenIncreaseCapacity_ThenPresenterCalledAndConfirmed() {
        CompanyPresenter presenter = presenterWithMap(EventStatus.DRAFT);
        when(presenter.increaseGACapacity(eq(EVENT_ID), eq(GA_ZONE_ID), any()))
                .thenReturn(ActionResult.success("GA capacity increased."));

        try (var ui = mockStatic(UiMessages.class)) {
            EditEventDialog dialog = openWith(presenter, draft());
            clickButton(dialog, "Increase");

            verify(presenter).increaseGACapacity(eq(EVENT_ID), eq(GA_ZONE_ID), any());
            ui.verify(() -> UiMessages.success("GA capacity increased."));
        }
    }

    @Test
    void GivenGaZone_WhenDecreaseCapacityFails_ThenSpecificReasonIsShown() {
        CompanyPresenter presenter = presenterWithMap(EventStatus.DRAFT);
        when(presenter.decreaseGACapacity(eq(EVENT_ID), eq(GA_ZONE_ID), any()))
                .thenReturn(ActionResult.failure("Cannot reduce capacity below tickets already sold"));

        try (var ui = mockStatic(UiMessages.class)) {
            EditEventDialog dialog = openWith(presenter, draft());
            clickButton(dialog, "Decrease");

            ui.verify(() -> UiMessages.error("Cannot reduce capacity below tickets already sold"));
        }
    }

    // ── Layout (publish gating) ──

    @Test
    void GivenDraftEvent_ThenLayoutEditorIsAvailable() {
        CompanyPresenter presenter = presenterWithMap(EventStatus.DRAFT);
        try (var ui = mockStatic(UiMessages.class)) {
            EditEventDialog dialog = openWith(presenter, draft());
            assertTrue(hasButton(dialog, "Save layout"), "DRAFT events should expose the layout editor");
        }
    }

    @Test
    void GivenPublishedEvent_ThenLayoutEditorIsLocked() {
        CompanyPresenter presenter = presenterWithMap(EventStatus.PUBLISHED);
        try (var ui = mockStatic(UiMessages.class)) {
            EditEventDialog dialog = openWith(presenter, published());
            assertFalse(hasButton(dialog, "Save layout"), "Published events must not allow layout edits");
            assertTrue(hasText(dialog, "The layout is locked once the event is published."));
        }
    }

    // ── Lifecycle ──

    @Test
    void GivenDraftEvent_WhenPublish_ThenPresenterCalledAndConfirmed() {
        CompanyPresenter presenter = presenterWithMap(EventStatus.DRAFT);
        when(presenter.publishEvent(EVENT_ID)).thenReturn(ActionResult.success("Event published."));

        try (var ui = mockStatic(UiMessages.class)) {
            EditEventDialog dialog = openWith(presenter, draft());
            clickButton(dialog, "Publish event");

            verify(presenter).publishEvent(EVENT_ID);
            ui.verify(() -> UiMessages.success("Event published."));
        }
    }

    @Test
    void GivenEvent_WhenCancel_ThenPresenterCalledAndConfirmed() {
        CompanyPresenter presenter = presenterWithMap(EventStatus.DRAFT);
        when(presenter.cancelEvent(EVENT_ID)).thenReturn(ActionResult.success("Event cancelled."));

        try (var ui = mockStatic(UiMessages.class)) {
            EditEventDialog dialog = openWith(presenter, draft());
            clickButton(dialog, "Cancel event");

            verify(presenter).cancelEvent(EVENT_ID);
            ui.verify(() -> UiMessages.success("Event cancelled."));
        }
    }

    @Test
    void GivenPublishFails_ThenSpecificReasonIsShown() {
        CompanyPresenter presenter = presenterWithMap(EventStatus.DRAFT);
        when(presenter.publishEvent(EVENT_ID))
                .thenReturn(ActionResult.failure("Insufficient permissions to publish events"));

        try (var ui = mockStatic(UiMessages.class)) {
            EditEventDialog dialog = openWith(presenter, draft());
            clickButton(dialog, "Publish event");

            ui.verify(() -> UiMessages.error("Insufficient permissions to publish events"));
        }
    }

    // ── Fixtures ──

    private CompanyPresenter presenterWithMap(EventStatus status) {
        CompanyPresenter presenter = mock(CompanyPresenter.class);
        when(presenter.loadEventMapForManagement(EVENT_ID))
                .thenReturn(EventMapResult.success("Event map loaded.", eventMap(status)));
        return presenter;
    }

    /** Build the dialog for the company and pick the event, mirroring a manager's flow. */
    private EditEventDialog openWith(CompanyPresenter presenter, EventSummaryDTO summary) {
        when(presenter.listCompanyEvents(COMPANY)).thenReturn(List.of(summary));
        EditEventDialog dialog = new EditEventDialog(presenter, COMPANY);
        selectEvent(dialog, summary);
        return dialog;
    }

    @SuppressWarnings("unchecked")
    private void selectEvent(EditEventDialog dialog, EventSummaryDTO summary) {
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
        return new EventMapDTO(
                EVENT_ID, "Demo Show", "Acme", status,
                Map.of("Floor", GA_ZONE_ID), List.of(ga), null);
    }

    // ── Component helpers (mirrors VenueDesignerDialogTest) ──

    private void setTextField(Component root, String label, String value) {
        for (HasValueAndElement<?, ?> field : findFieldsByLabel(root, label)) {
            if (field instanceof TextField tf) { tf.setValue(value); return; }
        }
        throw new AssertionError("TextField not found: " + label);
    }

    private void setBigDecimalField(Component root, String label, BigDecimal value) {
        for (HasValueAndElement<?, ?> field : findFieldsByLabel(root, label)) {
            if (field instanceof BigDecimalField bd) { bd.setValue(value); return; }
        }
        throw new AssertionError("BigDecimalField not found: " + label);
    }

    @SuppressWarnings("unused")
    private void setIntegerField(Component root, String label, int value) {
        for (HasValueAndElement<?, ?> field : findFieldsByLabel(root, label)) {
            if (field instanceof IntegerField intField) { intField.setValue(value); return; }
        }
        throw new AssertionError("IntegerField not found: " + label);
    }

    private void clickButton(EditEventDialog dialog, String text) {
        Button button = findButtonInComponent(dialog, text);
        if (button == null) {
            button = findButtonInElement(dialog.getFooter().getElement(), text);
        }
        if (button == null) throw new AssertionError("Button not found: " + text);
        button.click();
    }

    private boolean hasButton(EditEventDialog dialog, String text) {
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
