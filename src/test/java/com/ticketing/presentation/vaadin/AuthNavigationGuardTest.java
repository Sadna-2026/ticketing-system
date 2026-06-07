package com.ticketing.presentation.vaadin;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.ticketing.presentation.vaadin.views.AuthView;
import com.ticketing.presentation.vaadin.views.EventsView;
import com.vaadin.flow.router.BeforeEnterEvent;

@DisplayName("AuthNavigationGuard")
@ExtendWith(VaadinSessionExtension.class)
class AuthNavigationGuardTest {

    private final AuthNavigationGuard guard = new AuthNavigationGuard();

    @Test
    void GivenNoSession_WhenNavigatingAwayFromAuth_ThenReroutesToAuth() {
        BeforeEnterEvent event = navigationTo(EventsView.class);

        guard.beforeEnter(event);

        verify(event).rerouteTo(AuthView.class);
    }

    @Test
    void GivenNoSession_WhenNavigatingToAuth_ThenAllowsNavigation() {
        BeforeEnterEvent event = navigationTo(AuthView.class);

        guard.beforeEnter(event);

        verify(event, never()).rerouteTo(AuthView.class);
    }

    @Test
    void GivenGuestSession_WhenNavigatingAwayFromAuth_ThenAllowsNavigation() {
        SessionContext.setSessionToken("guest-token");
        BeforeEnterEvent event = navigationTo(EventsView.class);

        guard.beforeEnter(event);

        verify(event, never()).rerouteTo(AuthView.class);
    }

    @Test
    void GivenMemberSession_WhenNavigatingAwayFromAuth_ThenAllowsNavigation() {
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        BeforeEnterEvent event = navigationTo(EventsView.class);

        guard.beforeEnter(event);

        verify(event, never()).rerouteTo(AuthView.class);
    }

    private static BeforeEnterEvent navigationTo(Class<?> target) {
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);
        doReturn(target).when(event).getNavigationTarget();
        return event;
    }
}
