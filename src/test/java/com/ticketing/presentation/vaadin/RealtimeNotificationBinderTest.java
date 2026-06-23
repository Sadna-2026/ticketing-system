package com.ticketing.presentation.vaadin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.infrastructure.notification.NotificationListener;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter.RegistrationResult;

/**
 * The app-shell binder that gives a logged-in member real-time toasts on every route (#490).
 * MainLayout itself can't be unit-instantiated (its RouterLinks need a live router), so the
 * bind/rebind/unbind lifecycle lives here where it can be verified directly.
 */
@DisplayName("RealtimeNotificationBinder")
class RealtimeNotificationBinderTest {

    private static final NotificationListener LISTENER = message -> { };

    private NotificationsPresenter presenterRegistering(String memberId, String listenerId) {
        NotificationsPresenter presenter = mock(NotificationsPresenter.class);
        when(presenter.registerRealtimeListener(any()))
                .thenReturn(RegistrationResult.success(memberId, listenerId));
        return presenter;
    }

    @Test
    void GivenLoggedInMember_WhenSynced_ThenRegistersExactlyOnce() {
        NotificationsPresenter presenter = presenterRegistering("member-1", "listener-1");
        RealtimeNotificationBinder binder = new RealtimeNotificationBinder(presenter);

        binder.sync("member-1", LISTENER);

        verify(presenter, times(1)).registerRealtimeListener(any());
        assertTrue(binder.isBound());
    }

    @Test
    void GivenGuest_WhenSynced_ThenNothingIsRegistered() {
        NotificationsPresenter presenter = mock(NotificationsPresenter.class);
        RealtimeNotificationBinder binder = new RealtimeNotificationBinder(presenter);

        binder.sync(null, null);

        verify(presenter, never()).registerRealtimeListener(any());
        assertFalse(binder.isBound());
    }

    @Test
    void GivenAlreadyBoundToMember_WhenSyncedAgainForSameMember_ThenDoesNotRegisterTwice() {
        NotificationsPresenter presenter = presenterRegistering("member-1", "listener-1");
        RealtimeNotificationBinder binder = new RealtimeNotificationBinder(presenter);
        binder.sync("member-1", LISTENER);

        binder.sync("member-1", LISTENER); // e.g. a navigation/session refresh for the same member

        verify(presenter, times(1)).registerRealtimeListener(any());
        verify(presenter, never()).unregisterRealtimeListener(any(), any());
    }

    @Test
    void GivenBoundToMember_WhenSyncedForDifferentMember_ThenRebindsToTheNewMember() {
        NotificationsPresenter presenter = mock(NotificationsPresenter.class);
        when(presenter.registerRealtimeListener(any()))
                .thenReturn(RegistrationResult.success("member-1", "listener-1"))
                .thenReturn(RegistrationResult.success("member-2", "listener-2"));
        RealtimeNotificationBinder binder = new RealtimeNotificationBinder(presenter);
        binder.sync("member-1", LISTENER);

        binder.sync("member-2", LISTENER); // logged in as a different member

        verify(presenter).unregisterRealtimeListener("member-1", "listener-1");
        verify(presenter, times(2)).registerRealtimeListener(any());
        assertTrue(binder.isBound());
    }

    @Test
    void GivenBoundMember_WhenSyncedForGuest_ThenUnbinds() {
        NotificationsPresenter presenter = presenterRegistering("member-1", "listener-1");
        RealtimeNotificationBinder binder = new RealtimeNotificationBinder(presenter);
        binder.sync("member-1", LISTENER);

        binder.sync(null, null); // logout

        verify(presenter).unregisterRealtimeListener("member-1", "listener-1");
        assertFalse(binder.isBound());
    }

    @Test
    void GivenBoundMember_WhenUnbound_ThenListenerIsRemovedAndUnbindIsIdempotent() {
        NotificationsPresenter presenter = presenterRegistering("member-1", "listener-1");
        RealtimeNotificationBinder binder = new RealtimeNotificationBinder(presenter);
        binder.sync("member-1", LISTENER);

        binder.unbind();
        binder.unbind(); // second call must be a safe no-op

        verify(presenter, times(1)).unregisterRealtimeListener("member-1", "listener-1");
        assertFalse(binder.isBound());
    }
}
