package com.ticketing.presentation.vaadin.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.services.NotificationQueryService;
import com.ticketing.infrastructure.notification.NotificationListener;
import com.ticketing.infrastructure.notification.WebSocketNotificationService;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter.NotificationResult;
import com.ticketing.presentation.vaadin.presenters.NotificationsPresenter.RegistrationResult;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.server.VaadinSession;

@DisplayName("NotificationsPresenter")
class NotificationsPresenterTest {

    private NotificationQueryService notificationQueryService;
    private WebSocketNotificationService realtimeNotificationService;
    private NotificationsPresenter presenter;

    @BeforeEach
    void setUp() {
        notificationQueryService = mock(NotificationQueryService.class);
        realtimeNotificationService = mock(WebSocketNotificationService.class);
        presenter = new NotificationsPresenter(notificationQueryService, realtimeNotificationService);
        installVaadinSession();
    }

    @AfterEach
    void tearDown() {
        VaadinSession.setCurrent(null);
    }

    @Test
    void GivenNoMemberSession_WhenLoadingNotifications_ThenLoginRequiredMessageIsReturned() {
        NotificationResult result = presenter.loadPendingNotifications();

        assertFalse(result.success());
        assertEquals("Log in as a member to view notifications.", result.message());
        assertTrue(result.empty());
        verifyNoInteractions(notificationQueryService);
    }

    @Test
    void GivenMemberSession_WhenLoadingNotifications_ThenPendingMessagesAreReturned() {
        UUID memberId = UUID.randomUUID();
        SessionContext.setMemberId(memberId);
        when(notificationQueryService.getPendingNotifications(memberId.toString()))
                .thenReturn(List.of("Role offer accepted.", "Company ownership changed."));

        NotificationResult result = presenter.loadPendingNotifications();

        assertTrue(result.success());
        assertEquals("Loaded 2 notification(s).", result.message());
        assertEquals(List.of("Role offer accepted.", "Company ownership changed."), result.notifications());
    }

    @Test
    void GivenMemberSessionAndNoPendingMessages_WhenLoadingNotifications_ThenEmptyStateIsReturned() {
        UUID memberId = UUID.randomUUID();
        SessionContext.setMemberId(memberId);
        when(notificationQueryService.getPendingNotifications(memberId.toString())).thenReturn(List.of());

        NotificationResult result = presenter.loadPendingNotifications();

        assertTrue(result.success());
        assertTrue(result.empty());
        assertEquals("No pending notifications.", result.message());
    }

    @Test
    void GivenMemberSession_WhenClearingNotifications_ThenQueryServiceClearsPendingMessages() {
        UUID memberId = UUID.randomUUID();
        SessionContext.setMemberId(memberId);

        NotificationResult result = presenter.clearPendingNotifications();

        assertTrue(result.success());
        assertEquals("Notifications cleared.", result.message());
        assertTrue(result.empty());
        verify(notificationQueryService).clearPendingNotifications(memberId.toString());
    }

    @Test
    void GivenApplicationFailure_WhenLoadingNotifications_ThenGenericMessageIsReturned() {
        UUID memberId = UUID.randomUUID();
        SessionContext.setMemberId(memberId);
        when(notificationQueryService.getPendingNotifications(memberId.toString()))
                .thenThrow(new IllegalStateException("repository internals"));

        NotificationResult result = presenter.loadPendingNotifications();

        assertFalse(result.success());
        assertEquals("Could not load notifications. Please try again.", result.message());
    }

    @Test
    void GivenMemberSession_WhenRegisteringRealtimeListener_ThenListenerIsRegisteredForMember() {
        UUID memberId = UUID.randomUUID();
        SessionContext.setMemberId(memberId);
        NotificationListener listener = message -> { };
        when(realtimeNotificationService.registerListener(eq(memberId.toString()), eq(listener)))
                .thenReturn("listener-1");

        RegistrationResult result = presenter.registerRealtimeListener(listener);

        assertTrue(result.success());
        assertEquals(memberId.toString(), result.memberId());
        assertEquals("listener-1", result.registrationId());
        verify(realtimeNotificationService).registerListener(eq(memberId.toString()), eq(listener));
    }

    @Test
    void GivenNoMemberSession_WhenRegisteringRealtimeListener_ThenNoListenerIsRegistered() {
        RegistrationResult result = presenter.registerRealtimeListener(message -> { });

        assertFalse(result.success());
        assertEquals("Log in as a member to view notifications.", result.message());
        verifyNoInteractions(realtimeNotificationService);
    }

    @Test
    void GivenMemberId_WhenUnregisteringRealtimeListener_ThenListenerIsRemoved() {
        String memberId = UUID.randomUUID().toString();
        String registrationId = UUID.randomUUID().toString();

        presenter.unregisterRealtimeListener(memberId, registrationId);

        verify(realtimeNotificationService).removeListener(memberId, registrationId);
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
