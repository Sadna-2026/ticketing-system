package com.ticketing.presentation.vaadin.views;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketing.application.dto.QueueEntryDto;
import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.QueuePresenter;
import com.ticketing.presentation.vaadin.presenters.QueuePresenter.QueueResult;
import com.ticketing.presentation.vaadin.presenters.QueuePresenter.QueueStatusResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.BeforeLeaveEvent;
import com.vaadin.flow.router.BeforeLeaveObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

@Route(value = "queue", layout = MainLayout.class)
@PageTitle("Virtual Queue")
@SpringComponent
@UIScope
public class QueueView extends VerticalLayout implements BeforeEnterObserver, BeforeLeaveObserver {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final int POLL_INTERVAL_MS = 10_000;

    private final QueuePresenter presenter;

    // ── Entry form ──────────────────────────────────────────────────
    private final VerticalLayout entryForm = new VerticalLayout();
    private final Span sessionStatus = new Span();
    private final TextField eventIdField = new TextField("Event ID");
    private final Button joinButton = new Button("Join Queue");
    private final Span entryStatus = new Span();

    // ── Waiting room ────────────────────────────────────────────────
    private final VerticalLayout waitingRoom = new VerticalLayout();
    private final H2 waitingHeader = new H2("Virtual Queue");
    private final Div leaveWarning = leaveWarningBanner();
    private final Span positionLabel = new Span();
    private final ProgressBar progressBar = new ProgressBar();
    private final Span waitingStatus = new Span();
    private final Span entryDetails = new Span();
    private final Button refreshButton = new Button("Refresh status");
    private final Button leaveQueueButton = new Button("Leave queue", e -> confirmLeaveQueue());

    private UUID activeEventId;
    private Registration pollRegistration;
    private boolean inWaitingRoom = false;
    private boolean pollingDisabled = false;

    public QueueView(QueuePresenter presenter) {
        this.presenter = presenter;

        setPadding(true);
        setSpacing(true);
        setMaxWidth("660px");
        getStyle().set("margin", "0 auto");

        buildEntryForm();
        buildWaitingRoom();

        add(entryForm, waitingRoom);
        showEntryForm();
        addAttachListener(e -> {
            refreshEntrySessionStatus();
            if (inWaitingRoom) {
                startPolling();
            }
        });
    }

    // ── Vaadin lifecycle ────────────────────────────────────────────

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Auto-join from EventsView redirect: /queue?eventId=<uuid>
        Map<String, List<String>> params = event.getLocation().getQueryParameters().getParameters();
        List<String> eventIdParam = params.get("eventId");
        if (eventIdParam != null && !eventIdParam.isEmpty()) {
            UUID eventId = parseUuid(eventIdParam.get(0));
            if (eventId != null) {
                autoEnterWaitingRoom(eventId);
                return;
            }
        }
        showEntryForm();
        refreshEntrySessionStatus();
    }

    @Override
    public void beforeLeave(BeforeLeaveEvent event) {
        if (!inWaitingRoom) {
            return;
        }
        // Use JS window.confirm — Vaadin Dialog + postpone() causes the router to get
        // stuck after the user dismisses, making subsequent navigation impossible.
        BeforeLeaveEvent.ContinueNavigationAction action = event.postpone();
        getUI().ifPresent(ui -> ui.getPage()
                .executeJs("return window.confirm($0)",
                        "Leave the virtual queue? You will lose your place in the queue.")
                .then(Boolean.class, confirmed -> {
                    if (Boolean.TRUE.equals(confirmed)) {
                        stopPolling();
                        inWaitingRoom = false;
                        presenter.notifyLeft(activeEventId);
                        action.proceed();
                    }
                }));
    }

    private void confirmLeaveQueue() {
        Dialog confirm = new Dialog();
        confirm.setHeaderTitle("Leave the queue?");
        Paragraph text = new Paragraph(
                "Are you sure you want to leave? You will lose your place in the virtual queue.");
        Button leave = new Button("Yes, leave", e -> {
            stopPolling();
            inWaitingRoom = false;
            presenter.notifyLeft(activeEventId);
            confirm.close();
            UI.getCurrent().navigate(EventsView.class);
        });
        Button stay = new Button("Stay in queue", e -> confirm.close());
        leave.getStyle().set("color", "var(--lumo-error-color)");
        confirm.add(text, new HorizontalLayout(stay, leave));
        confirm.open();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        stopPolling();
        super.onDetach(detachEvent);
    }

    // ── Entry form ──────────────────────────────────────────────────

    private void buildEntryForm() {
        entryForm.setPadding(false);
        entryForm.setSpacing(true);

        eventIdField.setPlaceholder("e.g. 11111111-1111-1111-1111-111111111111");
        eventIdField.setWidthFull();
        joinButton.addClickListener(e -> joinQueue());
        entryStatus.getStyle().set("white-space", "pre-line");

        entryForm.add(
                new H2("Virtual Queue"),
                new Paragraph(
                        "Enter an event ID to join its virtual queue. "
                        + "If the event is under high load you will be placed in the queue "
                        + "and this page updates automatically when it is your turn. "
                        + "If no queue is active you are admitted directly."),
                sessionStatus,
                eventIdField,
                joinButton,
                entryStatus);
    }

    private void refreshEntrySessionStatus() {
        sessionStatus.setText(presenter.currentSessionLabel());
        boolean hasSession = !presenter.currentSessionState().noSession();
        joinButton.setEnabled(hasSession);
        eventIdField.setEnabled(hasSession);
    }

    private void joinQueue() {
        UUID eventId = parseUuid(eventIdField.getValue());
        if (eventId == null) {
            entryStatus.setText("Enter a valid event ID (UUID format).");
            UiMessages.error("Enter a valid event ID (UUID format).");
            return;
        }
        QueueResult result = presenter.tryEnterOrQueue(eventId);
        if (!result.success()) {
            entryStatus.setText(result.message());
            UiMessages.error(result.message());
            return;
        }
        if (result.queued()) {
            UiMessages.success(result.message());
            enterWaitingRoom(eventId, result.entry());
        } else {
            entryStatus.setText(result.message());
            UiMessages.success(result.message());
        }
    }

    // ── Waiting room ────────────────────────────────────────────────

    private void buildWaitingRoom() {
        waitingRoom.setPadding(false);
        waitingRoom.setSpacing(true);

        progressBar.setIndeterminate(true);
        progressBar.setWidthFull();

        refreshButton.addClickListener(e -> refreshStatus());

        waitingStatus.getStyle().set("white-space", "pre-line");
        entryDetails.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

        leaveQueueButton.getStyle().set("color", "var(--lumo-error-color)");

        waitingRoom.add(
                waitingHeader,
                leaveWarning,
                positionLabel,
                progressBar,
                waitingStatus,
                entryDetails,
                new HorizontalLayout(refreshButton, leaveQueueButton));
    }

    /** Called when we know the eventId and want to skip straight to the waiting room (from BeforeEnter redirect). */
    private void autoEnterWaitingRoom(UUID eventId) {
        // Check if already in queue (coming back to the page)
        QueueStatusResult status = presenter.checkQueueStatus(eventId);
        if (status.success() && status.inQueue()) {
            enterWaitingRoom(eventId, status.entry());
        } else {
            // Not yet in queue — try to join
            QueueResult join = presenter.tryEnterOrQueue(eventId);
            if (join.queued()) {
                enterWaitingRoom(eventId, join.entry());
            } else {
                // Direct entry or error — show the form with the message
                eventIdField.setValue(eventId.toString());
                entryStatus.setText(join.success()
                        ? join.message()
                        : join.message());
                if (!join.success()) {
                    UiMessages.error(join.message());
                } else {
                    UiMessages.success(join.message());
                }
                showEntryForm();
            }
        }
    }

    private void enterWaitingRoom(UUID eventId, QueueEntryDto entry) {
        activeEventId = eventId;
        inWaitingRoom = true;

        String eventName = presenter.loadEventName(eventId);
        waitingHeader.setText("Virtual Queue" + (eventName != null ? " — " + eventName : ""));

        renderWaitingState(entry, -1);
        showWaitingRoom();
        startPolling();
    }

    private void refreshStatus() {
        if (activeEventId == null) {
            return;
        }
        QueueStatusResult result = presenter.checkQueueStatus(activeEventId);
        waitingStatus.setText(result.message());

        if (!result.success()) {
            UiMessages.error(result.message());
            return;
        }
        if (result.admitted()) {
            handleAdmitted(result.entry());
        } else if (result.inQueue()) {
            renderWaitingState(result.entry(), result.waitingAhead());
        } else {
            // No longer in queue (expired / flushed)
            handleQueueExpired();
        }
    }

    private void renderWaitingState(QueueEntryDto entry, int waitingAhead) {
        if (waitingAhead >= 0) {
            positionLabel.setText(waitingAhead == 0
                    ? "You are next in line!"
                    : "Approximately " + waitingAhead + " people ahead of you.");
        } else {
            positionLabel.setText("You are in the virtual queue — hang tight.");
        }
        progressBar.setIndeterminate(true);

        if (entry != null) {
            String joined = entry.getJoinedAt() != null ? FORMATTER.format(entry.getJoinedAt()) : "—";
            entryDetails.setText("Status: " + entry.getStatus() + "  |  Joined: " + joined
                    + "  |  Entry ID: " + entry.getId());
        }
        if (waitingStatus.getText() == null || waitingStatus.getText().isEmpty()) {
            waitingStatus.setText("This page checks for updates every 10 seconds. Do not close or navigate away.");
        }
        refreshButton.setVisible(true);
    }

    private void handleAdmitted(QueueEntryDto entry) {
        stopPolling();
        inWaitingRoom = false;
        UUID admittedEventId = activeEventId;
        activeEventId = null;

        progressBar.setIndeterminate(false);
        progressBar.setValue(1.0);
        positionLabel.setText("It's your turn!");
        if (entry != null) {
            String joined = entry.getJoinedAt() != null ? FORMATTER.format(entry.getJoinedAt()) : "—";
            entryDetails.setText("Status: " + entry.getStatus() + "  |  Joined: " + joined);
        }
        refreshButton.setVisible(false);

        Span redirect = new Span("Redirecting to ticket selection in a moment…");
        redirect.getStyle().set("font-weight", "bold").set("color", "var(--lumo-success-color)");
        waitingRoom.add(redirect);

        UiMessages.success("It's your turn! Heading to ticket selection…");
        String target = admittedEventId != null
                ? "/events?eventId=" + admittedEventId
                : "/events";
        UI.getCurrent().getPage().executeJs("setTimeout(() => window.location.href=$0, 2500)", target);
    }

    private void handleQueueExpired() {
        stopPolling();
        inWaitingRoom = false;
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        positionLabel.setText("Your queue session has ended.");
        waitingStatus.setText(
                "Either all tickets have been sold or your queue slot has expired.\n"
                + "Return to the Events page to check availability.");
        refreshButton.setVisible(false);
        Span link = new Span("→ Go to Events");
        link.getStyle().set("cursor", "pointer").set("color", "var(--lumo-primary-color)");
        link.addClickListener(e -> UI.getCurrent().navigate(EventsView.class));
        waitingRoom.add(link);
        UiMessages.info("Your queue session has ended.");
    }

    // ── View toggling ───────────────────────────────────────────────

    private void showEntryForm() {
        entryForm.setVisible(true);
        waitingRoom.setVisible(false);
        refreshEntrySessionStatus();
    }

    private void showWaitingRoom() {
        entryForm.setVisible(false);
        waitingRoom.setVisible(true);
    }

    // ── Polling ─────────────────────────────────────────────────────

    private void startPolling() {
        if (pollingDisabled) {
            return;
        }
        getUI().ifPresent(ui -> {
            ui.setPollInterval(POLL_INTERVAL_MS);
            if (pollRegistration == null) {
                pollRegistration = ui.addPollListener(e -> refreshStatus());
            }
        });
    }

    private void stopPolling() {
        pollingDisabled = true;
        if (pollRegistration != null) {
            pollRegistration.remove();
            pollRegistration = null;
        }
        getUI().ifPresent(ui -> ui.setPollInterval(-1));
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private static Div leaveWarningBanner() {
        Span text = new Span("⚠  Do not leave this page — navigating away will remove you from the queue and you will lose your place.");
        Div banner = new Div(text);
        banner.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-left", "4px solid var(--lumo-error-color)")
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("color", "var(--lumo-body-text-color)");
        return banner;
    }

    private static UUID parseUuid(String value) {
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
