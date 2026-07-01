package com.ticketing.presentation.vaadin;

import com.ticketing.infrastructure.notification.NotificationListener;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter.RegistrationResult;

/**
 * Tracks a single real-time notification registration for the app shell ({@link MainLayout}), so a
 * logged-in member receives toasts on every route (#490) rather than only while the Notifications tab
 * is open. Kept separate from {@code MainLayout} so the bind/rebind/unbind logic is unit-testable
 * without a Vaadin router (the layout itself cannot be instantiated without one).
 *
 * <p>Not thread-safe: it is owned by one UI-scoped {@code MainLayout} and only touched on that UI's
 * thread (attach, detach, and session-change refreshes).
 */
final class RealtimeNotificationBinder {

    private final NotificationsPresenter presenter;

    private String registeredMemberId;
    private String registeredListenerId;

    RealtimeNotificationBinder(NotificationsPresenter presenter) {
        this.presenter = presenter;
    }

    /**
     * Ensures exactly the right listener is registered for {@code currentMemberId}: a no-op when
     * already bound to that member, a rebind when the member changed (login / switch user), and an
     * unbind when {@code currentMemberId} is {@code null} (guest / logged out). {@code listener} is
     * only used when a (re)registration actually happens.
     */
    void sync(String currentMemberId, NotificationListener listener) {
        if (currentMemberId != null
                && currentMemberId.equals(registeredMemberId)
                && registeredListenerId != null) {
            return; // already connected for this member
        }
        unbind();
        if (currentMemberId == null || listener == null) {
            return;
        }
        RegistrationResult result = presenter.registerRealtimeListener(listener);
        if (result != null && result.success()) {
            registeredMemberId = result.memberId();
            registeredListenerId = result.registrationId();
        }
    }

    /** Removes the current registration, if any. Safe to call repeatedly. */
    void unbind() {
        if (registeredMemberId != null && registeredListenerId != null) {
            presenter.unregisterRealtimeListener(registeredMemberId, registeredListenerId);
        }
        registeredMemberId = null;
        registeredListenerId = null;
    }

    boolean isBound() {
        return registeredListenerId != null;
    }
}
