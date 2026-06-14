package com.ticketing.presentation.vaadin.views;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.ticketing.application.dto.QueueEntryDto;
import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.QueuePresenter;
import com.ticketing.presentation.vaadin.presenters.QueuePresenter.QueueResult;
import com.ticketing.presentation.vaadin.presenters.QueuePresenter.QueueStatusResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

@Route(value = "queue", layout = MainLayout.class)
@PageTitle("Virtual Queue")
@SpringComponent
@UIScope
public class QueueView extends VerticalLayout {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final int POLL_INTERVAL_MS = 5_000;

    private final QueuePresenter presenter;

    private final Span sessionStatus = new Span();
    private final TextField eventIdField = new TextField("Event ID");
    private final Button joinButton = new Button("Join Queue");
    private final Span statusMessage = new Span();
    private final VerticalLayout entryDetails = new VerticalLayout();
    private final Button refreshButton = new Button("Refresh status");

    private UUID activeEventId;
    private Registration pollRegistration;

    public QueueView(QueuePresenter presenter) {
        this.presenter = presenter;

        setPadding(true);
        setSpacing(true);
        setMaxWidth("640px");
        getStyle().set("margin", "0 auto");

        eventIdField.setPlaceholder("e.g. 550e8400-e29b-41d4-a716-446655440000");
        eventIdField.setWidthFull();

        joinButton.addClickListener(e -> joinQueue());
        refreshButton.addClickListener(e -> refreshStatus());
        refreshButton.setVisible(false);

        entryDetails.setPadding(false);
        entryDetails.setSpacing(true);
        entryDetails.setVisible(false);

        add(
                new H2("Virtual Queue"),
                new Paragraph(
                        "Enter an event ID to join its virtual queue. "
                        + "If the event is under high load, you will be placed in the queue "
                        + "and this page will update automatically when it is your turn. "
                        + "If no queue is active, you will be admitted directly."),
                sessionStatus,
                eventIdField,
                joinButton,
                statusMessage,
                entryDetails,
                refreshButton);

        refreshSessionStatus();
        addAttachListener(e -> refreshSessionStatus());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        stopPolling();
        super.onDetach(detachEvent);
    }

    private void refreshSessionStatus() {
        sessionStatus.setText(presenter.currentSessionLabel());
        boolean hasSession = !presenter.currentSessionState().noSession();
        joinButton.setEnabled(hasSession);
        eventIdField.setEnabled(hasSession);
    }

    private void joinQueue() {
        UUID eventId = parseEventId(eventIdField.getValue());
        if (eventId == null) {
            statusMessage.setText("Enter a valid event ID (UUID format).");
            UiMessages.error("Enter a valid event ID (UUID format).");
            clearEntryDetails();
            return;
        }

        QueueResult result = presenter.tryEnterOrQueue(eventId);
        statusMessage.setText(result.message());

        if (!result.success()) {
            UiMessages.error(result.message());
            clearEntryDetails();
            return;
        }

        if (result.queued()) {
            UiMessages.success(result.message());
            activeEventId = eventId;
            showEntryDetails(result.entry(), false);
            startPolling();
        } else {
            UiMessages.success(result.message());
            clearEntryDetails();
            stopPolling();
            activeEventId = null;
        }
    }

    private void refreshStatus() {
        if (activeEventId == null) {
            return;
        }
        QueueStatusResult result = presenter.checkQueueStatus(activeEventId);
        statusMessage.setText(result.message());

        if (!result.success()) {
            UiMessages.error(result.message());
            return;
        }

        if (result.admitted()) {
            UiMessages.success(result.message());
            showEntryDetails(result.entry(), true);
            stopPolling();
            activeEventId = null;
        } else if (result.inQueue()) {
            showEntryDetails(result.entry(), false);
        } else {
            clearEntryDetails();
            stopPolling();
            activeEventId = null;
        }
    }

    private void showEntryDetails(QueueEntryDto entry, boolean admitted) {
        entryDetails.removeAll();
        entryDetails.add(new H3(admitted ? "Your turn!" : "Queue entry"));
        if (entry != null) {
            entryDetails.add(
                    new Span("Entry ID: " + entry.getId()),
                    new Span("Status: " + entry.getStatus()),
                    new Span("Joined at: " + (entry.getJoinedAt() != null
                            ? FORMATTER.format(entry.getJoinedAt()) : "—")));
        }
        if (admitted) {
            Span admittedBanner = new Span("You have been admitted — head to the Events page to browse and reserve tickets.");
            admittedBanner.getStyle().set("font-weight", "bold").set("color", "var(--lumo-success-color)");
            entryDetails.add(admittedBanner);
        }
        entryDetails.setVisible(true);
        refreshButton.setVisible(!admitted);
    }

    private void clearEntryDetails() {
        entryDetails.removeAll();
        entryDetails.setVisible(false);
        refreshButton.setVisible(false);
    }

    private void startPolling() {
        getUI().ifPresent(ui -> {
            ui.setPollInterval(POLL_INTERVAL_MS);
            if (pollRegistration == null) {
                pollRegistration = ui.addPollListener(e -> refreshStatus());
            }
        });
        refreshButton.setVisible(true);
    }

    private void stopPolling() {
        if (pollRegistration != null) {
            pollRegistration.remove();
            pollRegistration = null;
        }
        getUI().ifPresent(ui -> ui.setPollInterval(-1));
        refreshButton.setVisible(false);
    }

    private static UUID parseEventId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
