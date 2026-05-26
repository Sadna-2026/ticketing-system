package com.ticketing.presentation.vaadin.presenters;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketing.application.services.NotificationQueryService;
import com.ticketing.infrastructure.notification.NotificationListener;
import com.ticketing.infrastructure.notification.WebSocketNotificationService;
import com.ticketing.presentation.vaadin.util.SessionContext;

@Component
public class NotificationsPresenter {

    private static final Logger logger = LoggerFactory.getLogger(NotificationsPresenter.class);

    private static final String LOGIN_REQUIRED_MESSAGE = "Log in as a member to view notifications.";
    private static final String LOAD_FAILURE_MESSAGE = "Could not load notifications. Please try again.";
    private static final String CLEAR_FAILURE_MESSAGE = "Could not clear notifications. Please try again.";
    private static final String REALTIME_FAILURE_MESSAGE = "Could not connect to real-time notifications.";

    private final NotificationQueryService notificationQueryService;
    private final WebSocketNotificationService realtimeNotificationService;

    public NotificationsPresenter(
            NotificationQueryService notificationQueryService,
            WebSocketNotificationService realtimeNotificationService
    ) {
        this.notificationQueryService = notificationQueryService;
        this.realtimeNotificationService = realtimeNotificationService;
    }

    public NotificationResult loadPendingNotifications() {
        String memberId = currentMemberId();
        if (memberId == null) {
            return NotificationResult.failure(LOGIN_REQUIRED_MESSAGE);
        }

        try {
            List<String> notifications = notificationQueryService.getPendingNotifications(memberId);
            if (notifications.isEmpty()) {
                return NotificationResult.success("No pending notifications.", notifications);
            }
            return NotificationResult.success("Loaded " + notifications.size() + " notification(s).", notifications);
        } catch (RuntimeException ex) {
            logger.warn(LOAD_FAILURE_MESSAGE, ex);
            return NotificationResult.failure(LOAD_FAILURE_MESSAGE);
        }
    }

    public NotificationResult clearPendingNotifications() {
        String memberId = currentMemberId();
        if (memberId == null) {
            return NotificationResult.failure(LOGIN_REQUIRED_MESSAGE);
        }

        try {
            notificationQueryService.clearPendingNotifications(memberId);
            return NotificationResult.success("Notifications cleared.", List.of());
        } catch (RuntimeException ex) {
            logger.warn(CLEAR_FAILURE_MESSAGE, ex);
            return NotificationResult.failure(CLEAR_FAILURE_MESSAGE);
        }
    }

    public RegistrationResult registerRealtimeListener(NotificationListener listener) {
        String memberId = currentMemberId();
        if (memberId == null) {
            return RegistrationResult.failure(LOGIN_REQUIRED_MESSAGE, null);
        }

        try {
            realtimeNotificationService.registerListener(memberId, listener);
            return RegistrationResult.success(memberId);
        } catch (RuntimeException ex) {
            logger.warn(REALTIME_FAILURE_MESSAGE, ex);
            return RegistrationResult.failure(REALTIME_FAILURE_MESSAGE, memberId);
        }
    }

    public void unregisterRealtimeListener(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return;
        }

        realtimeNotificationService.removeListener(memberId);
    }

    public String currentMemberId() {
        UUID memberId = SessionContext.getMemberId();
        return memberId == null ? null : memberId.toString();
    }

    public record NotificationResult(boolean success, String message, List<String> notifications) {

        public NotificationResult {
            notifications = notifications == null ? List.of() : List.copyOf(notifications);
        }

        public static NotificationResult success(String message, List<String> notifications) {
            return new NotificationResult(true, message, notifications);
        }

        public static NotificationResult failure(String message) {
            return new NotificationResult(false, message, List.of());
        }

        public boolean empty() {
            return notifications.isEmpty();
        }
    }

    public record RegistrationResult(boolean success, String message, String memberId) {

        public static RegistrationResult success(String memberId) {
            return new RegistrationResult(true, "Real-time notifications connected.", memberId);
        }

        public static RegistrationResult failure(String message, String memberId) {
            return new RegistrationResult(false, message, memberId);
        }
    }
}
