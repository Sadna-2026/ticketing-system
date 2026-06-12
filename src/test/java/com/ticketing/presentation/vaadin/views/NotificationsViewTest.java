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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.infrastructure.notification.NotificationListener;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter.NotificationResult;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter.RegistrationResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.Command;

@DisplayName("NotificationsView")
@ExtendWith(VaadinSessionExtension.class)
class NotificationsViewTest {

    @Test
    void GivenNotificationsView_WhenRendered_ThenNotificationActionsAreAvailable() {
        NotificationsPresenter presenter = mock(NotificationsPresenter.class);

        NotificationsView view = new NotificationsView(presenter);

        assertTrue(hasButton(view, "Refresh notifications"));
        assertTrue(hasButton(view, "Clear notifications"));
        assertTrue(hasText(view, "No notifications to show."));
    }

    @Test
    void GivenPendingNotifications_WhenRefreshClicked_ThenMessagesAreDisplayed() {
        NotificationsPresenter presenter = mock(NotificationsPresenter.class);
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
        NotificationsPresenter presenter = mock(NotificationsPresenter.class);
        when(presenter.loadPendingNotifications())
                .thenReturn(NotificationResult.success("No pending notifications.", List.of()));
        NotificationsView view = new NotificationsView(presenter);

        clickButton(view, "Refresh notifications");

        assertTrue(hasText(view, "No pending notifications."));
        assertTrue(hasText(view, "No notifications to show."));
    }

    @Test
    void GivenClearSucceeds_WhenClearClicked_ThenVisibleNotificationsAreRemoved() {
        NotificationsPresenter presenter = mock(NotificationsPresenter.class);
        when(presenter.clearPendingNotifications())
                .thenReturn(NotificationResult.success("Notifications cleared.", List.of()));
        NotificationsView view = new NotificationsView(presenter);
        view.receiveRealtimeNotification("Live update.");

        clickButton(view, "Clear notifications");

        assertTrue(hasText(view, "Notifications cleared."));
        assertTrue(hasText(view, "No notifications to show."));
    }

    @Test
    void GivenRealtimeNotification_WhenReceived_ThenMessageIsShownInPanel() {
        NotificationsPresenter presenter = mock(NotificationsPresenter.class);
        NotificationsView view = new NotificationsView(presenter);

        view.receiveRealtimeNotification("Your role appointment was approved.");

        assertTrue(hasText(view, "Your role appointment was approved."));
        assertTrue(hasText(view, "Showing 1 notification(s)."));
    }

    @Test
    void GivenViewAttached_WhenRealtimeRegistrationSucceeds_ThenPendingNotificationsAreNotReloadedOverFlush() {
        NotificationsPresenter presenter = mock(NotificationsPresenter.class);
        when(presenter.registerRealtimeListener(any()))
                .thenReturn(RegistrationResult.success("member-1", "listener-1"));
        NotificationsView view = new NotificationsView(presenter);

        attachViewToCurrentUi(view);

        assertTrue(hasText(view, "Real-time notifications connected."));
        assertTrue(hasText(view, "No pending notifications."));
        verify(presenter, never()).loadPendingNotifications();
    }

    @Test
    void GivenRegisteredView_WhenServicePushesNotification_ThenListenerUpdatesTheUi() {
        NotificationsPresenter presenter = mock(NotificationsPresenter.class);
        AtomicReference<NotificationListener> captured = new AtomicReference<>();
        when(presenter.registerRealtimeListener(any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return RegistrationResult.success("member-1", "listener-1");
        });
        NotificationsView view = new NotificationsView(presenter);
        attachViewToSynchronousUi(view);

        assertNotNull(captured.get(), "view did not register a realtime listener on attach");
        captured.get().onMessage("Role appointment offer received.");

        assertTrue(hasText(view, "Role appointment offer received."));
        assertTrue(hasText(view, "Showing 1 notification(s)."));
    }

    @Test
    void GivenRegisteredView_WhenDetached_ThenOnlyOwnRealtimeRegistrationIsRemoved() {
        NotificationsPresenter presenter = mock(NotificationsPresenter.class);
        when(presenter.registerRealtimeListener(any()))
                .thenReturn(RegistrationResult.success("member-1", "listener-1"));
        NotificationsView view = new NotificationsView(presenter);
        attachViewToCurrentUi(view);

        UI.getCurrent().remove(view);

        verify(presenter).unregisterRealtimeListener("member-1", "listener-1");
    }

    @Test
    void GivenLoadFailure_WhenRefreshClicked_ThenSpecificFailureReasonIsShown() {
        NotificationsPresenter presenter = mock(NotificationsPresenter.class);
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
        NotificationsView view = new NotificationsView(mock(NotificationsPresenter.class));
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);

        view.beforeEnter(event);

        verify(event).forwardTo(HomeView.class);
    }

    @Test
    void GivenMemberSession_WhenEnteringNotifications_ThenNavigationIsAllowed() {
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        NotificationsView view = new NotificationsView(mock(NotificationsPresenter.class));
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);

        view.beforeEnter(event);

        verify(event, never()).forwardTo(HomeView.class);
    }

    /** Makes a {@link #synchronousUi()} current so the {@code beforeEnter} guard's inline popup can run. */
    private void setSynchronousCurrentUi() {
        UI.setCurrent(synchronousUi());
    }

    private void attachViewToCurrentUi(Component view) {
        UI ui = new UI();
        ui.add(view);
        UI.setCurrent(ui);
    }

    /** Attaches the view to a {@link #synchronousUi()} and makes it current. */
    private void attachViewToSynchronousUi(Component view) {
        UI ui = synchronousUi();
        ui.add(view);
        UI.setCurrent(ui);
    }

    /**
     * Builds a UI whose {@link UI#access(Command)} runs the command inline. A bare test UI has no
     * {@link com.vaadin.flow.server.VaadinSession}, so the real {@code ui.access(...)} the view uses
     * to marshal push callbacks (and the {@code beforeEnter} guard's info popup) onto the UI thread
     * throws {@link com.vaadin.flow.component.UIDetachedException}. Running the command inline lets a
     * test exercise that path without a live WebSocket/session.
     */
    private static UI synchronousUi() {
        return new UI() {
            @Override
            public Future<Void> access(Command command) {
                command.execute();
                return CompletableFuture.completedFuture(null);
            }
        };
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
