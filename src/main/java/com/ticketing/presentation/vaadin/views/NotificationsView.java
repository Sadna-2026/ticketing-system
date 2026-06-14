package com.ticketing.presentation.vaadin.views;

import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.PendingRoleOfferOption;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter.NotificationResult;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter.RegistrationResult;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "notifications", layout = MainLayout.class)
@PageTitle("Notifications")
public class NotificationsView extends VerticalLayout implements BeforeEnterObserver {

    private final NotificationsPresenter presenter;

    private final Span connectionStatus = new Span("Open this page as a logged-in member to connect notifications.");
    private final Span notificationsStatus = new Span("Notifications have not been loaded yet.");
    private final VerticalLayout notificationsList = new VerticalLayout();
    private final VerticalLayout roleOffersList = new VerticalLayout();
    private final Span roleOffersStatus = new Span();
    private final Button refresh = new Button("Refresh notifications");
    private final Button clear = new Button("Clear notifications");

    private String registeredMemberId;
    private String registeredListenerId;
    private int visibleNotificationCount;

    public NotificationsView(NotificationsPresenter presenter) {
        this.presenter = presenter;

        setPadding(true);
        setSpacing(true);
        setMaxWidth("900px");
        getStyle().set("margin", "0 auto");

        configureActions();
        renderNotifications(java.util.List.of());
        renderRoleOffers();

        roleOffersList.setPadding(false);
        roleOffersList.setSpacing(true);

        add(
                new H2("Notifications"),
                new Paragraph("View pending account notifications and receive new notifications while this page is open."),
                connectionStatus,
                new H3("Pending role offers"),
                roleOffersStatus,
                roleOffersList,
                actions(),
                new H3("Notification history"),
                notificationsStatus,
                notificationsList
        );

        addAttachListener(event -> {
            registerRealtimeNotifications(event.getUI());
            renderRoleOffers();
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
            registeredListenerId = null;
            connectionStatus.setText(result.message());
            notificationsStatus.setText(result.message());
            UiMessages.info(result.message());
            return;
        }

        registeredMemberId = result.memberId();
        registeredListenerId = result.registrationId();
        connectionStatus.setText(result.message());
        notificationsStatus.setText("No pending notifications.");
    }

    private void unregisterRealtimeNotifications() {
        if (registeredMemberId == null || registeredListenerId == null) {
            return;
        }

        presenter.unregisterRealtimeListener(registeredMemberId, registeredListenerId);
        registeredMemberId = null;
        registeredListenerId = null;
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

        renderRoleOffers();

        if (message.startsWith("You have a new role offer")) {
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

    void renderRoleOffers() {
        java.util.List<PendingRoleOfferOption> offers = presenter.listPendingRoleOffers();
        roleOffersList.removeAll();
        if (offers.isEmpty()) {
            roleOffersStatus.setText("No pending role offers.");
            return;
        }
        roleOffersStatus.setText(offers.size() + " pending offer(s).");
        offers.forEach(offer -> roleOffersList.add(offerCard(offer)));
    }

    private VerticalLayout offerCard(PendingRoleOfferOption offer) {
        Span label = new Span(offer.label());
        label.getStyle().set("font-weight", "600");

        Button accept = new Button("Accept", e -> {
            ActionResult result = presenter.respondToRoleOffer(offer.offerId(), true);
            UiMessages.info(result.message());
            renderRoleOffers();
        });
        Button reject = new Button("Reject", e -> {
            ActionResult result = presenter.respondToRoleOffer(offer.offerId(), false);
            UiMessages.info(result.message());
            renderRoleOffers();
        });
        accept.getThemeNames().add("primary");
        reject.getThemeNames().add("error");

        HorizontalLayout buttons = new HorizontalLayout(accept, reject);
        buttons.setAlignItems(Alignment.BASELINE);
        buttons.setPadding(false);

        VerticalLayout card = new VerticalLayout(label, buttons);
        card.setPadding(true);
        card.setSpacing(false);
        card.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-left", "4px solid var(--lumo-primary-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("background", "var(--lumo-base-color)");
        return card;
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

    /**
     * Intercepts navigation before entering the view.
     * Unauthenticated users (e.g., guests or users with no session) are prevented from accessing
     * the notifications page. They are shown an informational popup and redirected to the Home page.
     *
     * @param event the before enter event
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!SessionContext.currentUiState().loggedInMember()) {
            event.forwardTo(HomeView.class);
            UI.getCurrent().access(() -> {
                UiMessages.error("You cannot access the notifications page as a guest. Please log in first.");
            });
        }
    }
}
