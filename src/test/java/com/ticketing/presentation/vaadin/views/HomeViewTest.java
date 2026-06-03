package com.ticketing.presentation.vaadin.views;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.router.BeforeEnterEvent;

@DisplayName("HomeView")
@ExtendWith(VaadinSessionExtension.class)
class HomeViewTest {

    @BeforeEach
    void setUp() {
    }

    @Test
    void GivenNoSession_WhenEnteringHome_ThenForwardsToAuth() {
        HomeView view = new HomeView();
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);

        view.beforeEnter(event);

        verify(event).forwardTo(AuthView.class);
    }

    @Test
    void GivenMemberSession_WhenEnteringHome_ThenDoesNotRedirect() {
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        HomeView view = new HomeView();
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);

        view.beforeEnter(event);

        verify(event, never()).forwardTo(any(Class.class));
    }

    @Test
    void GivenGuestSession_WhenEnteringHome_ThenDoesNotRedirect() {
        SessionContext.setSessionToken("guest-token");
        HomeView view = new HomeView();
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);

        view.beforeEnter(event);

        verify(event, never()).forwardTo(any(Class.class));
    }
}
