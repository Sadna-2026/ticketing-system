package com.ticketing.presentation.vaadin.views;

import java.util.List;
import java.util.UUID;

import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.presentation.vaadin.components.VenueLayoutEditorComponent;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventMapResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Edit the layout of an existing event. This dialog exposes ONLY the layout grid
 * and visual components. It is used by managers with the MAP_DEFINITION permission
 * who cannot edit event details, zones, or policies.
 */
public class EditVenueLayoutDialog extends Dialog {



    private final CompanyPresenter presenter;
    private final String companyName;
    private UUID eventId;
    private EventStatus status;

    // Event selection
    private final ComboBox<EventSummaryDTO> eventPicker = new ComboBox<>("Event to manage");
    private final VerticalLayout body = new VerticalLayout();

    // Layout editor
    private final VenueLayoutEditorComponent layoutEditor = new VenueLayoutEditorComponent();
    private final Span layoutStatus = new Span();

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
            ActionResult result = presenter.redefineVenue(
                    eventId, r.rows(), r.cols(), r.zoneSpecs(), r.sectionToZone(), r.cellSpecs());
            layoutStatus.setText(result.message());
            showResult(result.success(), result.message());
            if (result.success()) {
                loadEventMap();
                body.removeAll();
                body.add(buildLayoutSection());
            }
        } catch (IllegalStateException ex) {
            UiMessages.error(ex.getMessage());
        }
    }

    private void validateLayout() {
        ActionResult result = presenter.validateEventLayout(eventId);
        layoutStatus.setText(result.message());
        showResult(result.success(), result.message());
    }

    // ── Loading ──

    private void loadEventMap() {
        EventMapResult result = presenter.loadEventMapForManagement(eventId);
        if (result == null || !result.success() || result.eventMap() == null) {
            layoutEditor.setEventMap(null);
            return;
        }
        EventMapDTO map = result.eventMap();
        this.status = map.status();
        layoutEditor.setEventMap(map);
    }

    // ── Helpers ──

    private static void showResult(boolean success, String message) {
        if (success) {
            UiMessages.success(message);
        } else {
            UiMessages.error(message);
        }
    }



}
