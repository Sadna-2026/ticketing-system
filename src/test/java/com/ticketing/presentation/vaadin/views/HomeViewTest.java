package com.ticketing.presentation.vaadin.views;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.VaadinSession;

@DisplayName("HomeView")
class HomeViewTest {

    @BeforeEach
    void setUp() {
        installVaadinSession();
    }

    @AfterEach
    void tearDown() {
        VaadinSession.setCurrent(null);
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

    private void installVaadinSession() {
        VaadinSession session = mock(VaadinSession.class);
        Map<String, Object> attributes = new HashMap<>();

        doAnswer(invocation -> {
            attributes.put(invocation.getArgument(0, String.class), invocation.getArgument(1));
            return null;
        }).when(session).setAttribute(anyString(), nullable(Object.class));

        when(session.getAttribute(anyString())).thenAnswer(invocation ->
                attributes.get(invocation.getArgument(0, String.class))
        );

        VaadinSession.setCurrent(session);
    }
}
