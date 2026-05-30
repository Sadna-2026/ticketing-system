package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter.NotificationResult;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter.RegistrationResult;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;

@DisplayName("NotificationsView")
class NotificationsViewTest {

    @BeforeEach
    void setUp() {
        UI.setCurrent(new UI());
    }

    @AfterEach
    void tearDown() {
        UI.setCurrent(null);
    }

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

        UI.getCurrent().add(view);

        assertTrue(hasText(view, "Real-time notifications connected."));
        assertTrue(hasText(view, "No pending notifications."));
        verify(presenter, never()).loadPendingNotifications();
    }

    @Test
    void GivenRegisteredView_WhenDetached_ThenOnlyOwnRealtimeRegistrationIsRemoved() {
        NotificationsPresenter presenter = mock(NotificationsPresenter.class);
        when(presenter.registerRealtimeListener(any()))
                .thenReturn(RegistrationResult.success("member-1", "listener-1"));
        NotificationsView view = new NotificationsView(presenter);
        UI.getCurrent().add(view);

        UI.getCurrent().remove(view);

        verify(presenter).unregisterRealtimeListener("member-1", "listener-1");
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
