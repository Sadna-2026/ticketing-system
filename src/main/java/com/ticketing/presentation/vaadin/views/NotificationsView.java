package com.ticketing.presentation.vaadin.views;

import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter.NotificationResult;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter.RegistrationResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "notifications", layout = MainLayout.class)
@PageTitle("Notifications")
public class NotificationsView extends VerticalLayout {

    private final NotificationsPresenter presenter;

    private final Span connectionStatus = new Span("Open this page as a logged-in member to connect notifications.");
    private final Span notificationsStatus = new Span("Notifications have not been loaded yet.");
    private final VerticalLayout notificationsList = new VerticalLayout();
    private final Button refresh = new Button("Refresh notifications");
    private final Button clear = new Button("Clear notifications");

    private String registeredMemberId;
    private int visibleNotificationCount;

    public NotificationsView(NotificationsPresenter presenter) {
        this.presenter = presenter;

        setPadding(true);
        setSpacing(true);
        setMaxWidth("900px");
        getStyle().set("margin", "0 auto");

        configureActions();
        renderNotifications(java.util.List.of());

        add(
                new H2("Notifications"),
                new Paragraph("View pending account notifications and receive new notifications while this page is open."),
                connectionStatus,
                actions(),
                new H3("Notification history"),
                notificationsStatus,
                notificationsList
        );

        addAttachListener(event -> {
            registerRealtimeNotifications(event.getUI());
            loadPendingNotifications(false);
        });
        addDetachListener(event -> unregisterRealtimeNotifications());
    }

    private void configureActions() {
        refresh.addClickListener(event -> loadPendingNotifications(true));
        clear.addClickListener(event -> clearNotifications());
    }

    private HorizontalLayout actions() {
        HorizontalLayout layout = new HorizontalLayout(refresh, clear);
        layout.setAlignItems(Alignment.BASELINE);
        return layout;
    }

    private void registerRealtimeNotifications(UI ui) {
        unregisterRealtimeNotifications();

        RegistrationResult result = presenter.registerRealtimeListener(message ->
                ui.access(() -> receiveRealtimeNotification(message))
        );
        if (!result.success()) {
            registeredMemberId = null;
            connectionStatus.setText(result.message());
            UiMessages.info(result.message());
            return;
        }

        registeredMemberId = result.memberId();
        connectionStatus.setText(result.message());
    }

    private void unregisterRealtimeNotifications() {
        if (registeredMemberId == null) {
            return;
        }

        presenter.unregisterRealtimeListener(registeredMemberId);
        registeredMemberId = null;
    }

    private void loadPendingNotifications(boolean showToast) {
        NotificationResult result = presenter.loadPendingNotifications();
        if (!result.success()) {
            renderNotifications(java.util.List.of());
            notificationsStatus.setText(result.message());
            if (showToast) {
                UiMessages.error(result.message());
            }
            return;
        }

        renderNotifications(result.notifications());
        notificationsStatus.setText(result.message());
        if (showToast) {
            if (result.empty()) {
                UiMessages.info(result.message());
            } else {
                UiMessages.success(result.message());
            }
        }
    }

    private void clearNotifications() {
        NotificationResult result = presenter.clearPendingNotifications();
        if (!result.success()) {
            notificationsStatus.setText(result.message());
            UiMessages.error(result.message());
            return;
        }

        renderNotifications(java.util.List.of());
        notificationsStatus.setText(result.message());
        UiMessages.success(result.message());
    }

    void receiveRealtimeNotification(String message) {
        if (message == null || message.isBlank()) {
            return;
        }

        if (visibleNotificationCount == 0) {
            notificationsList.removeAll();
        }
        notificationsList.add(notificationCard(message));
        visibleNotificationCount++;
        notificationsStatus.setText("Showing " + visibleNotificationCount + " notification(s).");
        UiMessages.info(message);
    }

    private void renderNotifications(java.util.List<String> notifications) {
        notificationsList.removeAll();
        visibleNotificationCount = notifications.size();

        if (notifications.isEmpty()) {
            notificationsList.add(new Paragraph("No notifications to show."));
            return;
        }

        notifications.forEach(message -> notificationsList.add(notificationCard(message)));
    }

    private Span notificationCard(String message) {
        Span card = new Span(message);
        card.getStyle()
                .set("display", "block")
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-s)")
                .set("background", "var(--lumo-base-color)");
        return card;
    }
}
