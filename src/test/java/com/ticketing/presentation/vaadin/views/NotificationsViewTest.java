package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.PendingRoleOfferOption;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter.NotificationResult;
import com.ticketing.presentation.vaadin.testsupport.SynchronousUi;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.router.BeforeEnterEvent;

@DisplayName("NotificationsView")
@ExtendWith(VaadinSessionExtension.class)
class NotificationsViewTest {

    /** Creates a presenter mock that stubs listPendingRoleOffers() to return an empty list by default. */
    private NotificationsPresenter mockPresenter() {
        NotificationsPresenter presenter = mock(NotificationsPresenter.class);
        when(presenter.listPendingRoleOffers()).thenReturn(List.of());
        return presenter;
    }

    @Test
    void GivenNotificationsView_WhenRendered_ThenNotificationActionsAreAvailable() {
        NotificationsPresenter presenter = mockPresenter();

        NotificationsView view = new NotificationsView(presenter);

        assertTrue(hasButton(view, "Refresh notifications"));
        assertTrue(hasButton(view, "Clear notifications"));
        assertTrue(hasText(view, "No notifications to show."));
    }

    @Test
    void GivenPendingNotifications_WhenRefreshClicked_ThenMessagesAreDisplayed() {
        NotificationsPresenter presenter = mockPresenter();
        when(presenter.loadPendingNotifications())
                .thenReturn(NotificationResult.success("Loaded 2 notification(s).", List.of("Offer accepted.", "Owner changed.")));
        NotificationsView view = new NotificationsView(presenter);

        clickButton(view, "Refresh notifications");

        assertTrue(hasText(view, "Loaded 2 notification(s)."));
        assertTrue(hasText(view, "Offer accepted."));
        assertTrue(hasText(view, "Owner changed."));
    }

    @Test
    void GivenNoPendingNotifications_WhenRefreshClicked_ThenEmptyStateIsDisplayed() {
        NotificationsPresenter presenter = mockPresenter();
        when(presenter.loadPendingNotifications())
                .thenReturn(NotificationResult.success("No pending notifications.", List.of()));
        NotificationsView view = new NotificationsView(presenter);

        clickButton(view, "Refresh notifications");

        assertTrue(hasText(view, "No pending notifications."));
        assertTrue(hasText(view, "No notifications to show."));
    }

    @Test
    void GivenClearSucceeds_WhenClearClicked_ThenVisibleNotificationsAreRemoved() {
        NotificationsPresenter presenter = mockPresenter();
        when(presenter.loadPendingNotifications())
                .thenReturn(NotificationResult.success("Loaded 1 notification(s).", List.of("Live update.")));
        when(presenter.clearPendingNotifications())
                .thenReturn(NotificationResult.success("Notifications cleared.", List.of()));
        NotificationsView view = new NotificationsView(presenter);
        clickButton(view, "Refresh notifications");
        assertTrue(hasText(view, "Live update."));

        clickButton(view, "Clear notifications");

        assertTrue(hasText(view, "Notifications cleared."));
        assertTrue(hasText(view, "No notifications to show."));
    }

    @Test
    void GivenViewAttached_WhenLoggedInMember_ThenHistoryIsLoadedAndNoRealtimeListenerIsRegistered() {
        NotificationsPresenter presenter = mockPresenter();
        when(presenter.loadPendingNotifications())
                .thenReturn(NotificationResult.success("Loaded 1 notification(s).", List.of("Earlier message.")));
        NotificationsView view = new NotificationsView(presenter);

        attachViewToCurrentUi(view);

        // The inbox loads persisted history on attach...
        assertTrue(hasText(view, "Earlier message."));
        // ...but real-time delivery is owned by MainLayout (#490), so this tab registers no listener
        // of its own (that would double-toast and tear delivery down when navigating away).
        verify(presenter, never()).registerRealtimeListener(any());
    }

    @Test
    void GivenLoadFailure_WhenRefreshClicked_ThenSpecificFailureReasonIsShown() {
        NotificationsPresenter presenter = mockPresenter();
        when(presenter.loadPendingNotifications())
                .thenReturn(NotificationResult.failure("Log in as a member to view notifications."));
        NotificationsView view = new NotificationsView(presenter);

        clickButton(view, "Refresh notifications");

        assertTrue(hasText(view, "Log in as a member to view notifications."));
        assertTrue(hasText(view, "No notifications to show."));
    }

    @Test
    void GivenGuestSession_WhenEnteringNotifications_ThenForwardedToHome() {
        setSynchronousCurrentUi();
        NotificationsView view = new NotificationsView(mockPresenter());
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);

        view.beforeEnter(event);

        verify(event).forwardTo(HomeView.class);
    }

    @Test
    void GivenMemberSession_WhenEnteringNotifications_ThenNavigationIsAllowed() {
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        NotificationsView view = new NotificationsView(mockPresenter());
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);

        view.beforeEnter(event);

        verify(event, never()).forwardTo(HomeView.class);
    }

    @Test
    void GivenPendingRoleOffers_WhenPageLoads_ThenOfferCardsAreShown() {
        UUID offerId = UUID.randomUUID();
        NotificationsPresenter presenter = mockPresenter();
        when(presenter.listPendingRoleOffers()).thenReturn(List.of(
                new PendingRoleOfferOption(offerId, "Acme", StaffAppointment.StaffRole.MANAGER)));

        NotificationsView view = new NotificationsView(presenter);

        assertTrue(hasText(view, "Acme — MANAGER"));
        assertTrue(hasText(view, "1 pending offer(s)."));
        assertTrue(hasButton(view, "Accept"));
        assertTrue(hasButton(view, "Reject"));
    }

    @Test
    void GivenPendingRoleOffer_WhenAcceptClicked_ThenPresenterRespondCalledWithTrue() {
        UUID offerId = UUID.randomUUID();
        NotificationsPresenter presenter = mockPresenter();
        when(presenter.listPendingRoleOffers()).thenReturn(List.of(
                new PendingRoleOfferOption(offerId, "Acme", StaffAppointment.StaffRole.MANAGER)));
        when(presenter.respondToRoleOffer(offerId, true)).thenReturn(ActionResult.success("Role offer accepted."));

        NotificationsView view = new NotificationsView(presenter);
        clickButton(view, "Accept");

        verify(presenter).respondToRoleOffer(offerId, true);
    }

    @Test
    void GivenPendingRoleOffer_WhenRejectClicked_ThenPresenterRespondCalledWithFalse() {
        UUID offerId = UUID.randomUUID();
        NotificationsPresenter presenter = mockPresenter();
        when(presenter.listPendingRoleOffers()).thenReturn(List.of(
                new PendingRoleOfferOption(offerId, "Acme", StaffAppointment.StaffRole.MANAGER)));
        when(presenter.respondToRoleOffer(offerId, false)).thenReturn(ActionResult.success("Role offer rejected."));

        NotificationsView view = new NotificationsView(presenter);
        clickButton(view, "Reject");
        com.ticketing.presentation.vaadin.testsupport.ConfirmDialogTestSupport.confirm();

        verify(presenter).respondToRoleOffer(offerId, false);
    }

    @Test
    void GivenNoPendingRoleOffers_WhenPageLoads_ThenEmptyStateIsShown() {
        NotificationsPresenter presenter = mockPresenter();

        NotificationsView view = new NotificationsView(presenter);

        assertTrue(hasText(view, "No pending role offers."));
    }

    /** Makes a {@link SynchronousUi} current so the {@code beforeEnter} guard's inline popup can run. */
    private void setSynchronousCurrentUi() {
        UI.setCurrent(SynchronousUi.create());
    }

    private void attachViewToCurrentUi(Component view) {
        UI ui = new UI();
        ui.add(view);
        UI.setCurrent(ui);
    }

    private boolean hasButton(Component root, String text) {
        if (root instanceof Button button && text.equals(button.getText())) {
            return true;
        }
        return root.getChildren().anyMatch(child -> hasButton(child, text));
    }

    private void clickButton(Component root, String text) {
        Button button = findButton(root, text);
        assertNotNull(button, "button not found: " + text);
        button.click();
    }

    private Button findButton(Component root, String text) {
        if (root instanceof Button button && text.equals(button.getText())) {
            return button;
        }
        return root.getChildren()
                .map(child -> findButton(child, text))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean hasText(Component root, String text) {
        if (root instanceof HasText hasText && text.equals(hasText.getText())) {
            return true;
        }
        return root.getChildren().anyMatch(child -> hasText(child, text));
    }
}
