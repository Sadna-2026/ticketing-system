package com.ticketing.presentation.vaadin.presenters;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.MemberService;
import com.ticketing.application.services.NotificationQueryService;
import com.ticketing.infrastructure.notification.NotificationListener;
import com.ticketing.infrastructure.notification.WebSocketNotificationService;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.PendingRoleOfferOption;
import com.ticketing.presentation.vaadin.util.PresenterErrorClassifier;
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
    private final MemberService memberService;
    private final CompanyService companyService;

    public NotificationsPresenter(
            NotificationQueryService notificationQueryService,
            WebSocketNotificationService realtimeNotificationService,
            MemberService memberService,
            CompanyService companyService
    ) {
        this.notificationQueryService = notificationQueryService;
        this.realtimeNotificationService = realtimeNotificationService;
        this.memberService = memberService;
        this.companyService = companyService;
    }

    public NotificationResult loadPendingNotifications() {
        String memberId = currentMemberId();
        if (memberId == null) {
            return NotificationResult.failure(LOGIN_REQUIRED_MESSAGE);
        }

        try {
            List<String> allNotifications = notificationQueryService.getNotificationHistory(memberId);
            List<String> notifications = allNotifications.stream()
                    .filter(msg -> !msg.startsWith("You have a new role offer"))
                    .toList();
            if (notifications.isEmpty()) {
                return NotificationResult.success("No pending notifications.", notifications);
            }
            return NotificationResult.success("Loaded " + notifications.size() + " notification(s).", notifications);
        } catch (RuntimeException ex) {
            return NotificationResult.failure(userMessage(ex, LOAD_FAILURE_MESSAGE));
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
            return NotificationResult.failure(userMessage(ex, CLEAR_FAILURE_MESSAGE));
        }
    }

    public RegistrationResult registerRealtimeListener(NotificationListener listener) {
        String memberId = currentMemberId();
        if (memberId == null) {
            return RegistrationResult.failure(LOGIN_REQUIRED_MESSAGE, null, null);
        }

        try {
            String registrationId = realtimeNotificationService.registerListener(memberId, listener);
            return RegistrationResult.success(memberId, registrationId);
        } catch (RuntimeException ex) {
            return RegistrationResult.failure(userMessage(ex, REALTIME_FAILURE_MESSAGE), memberId, null);
        }
    }

    public void unregisterRealtimeListener(String memberId, String registrationId) {
        if (memberId == null || memberId.isBlank() || registrationId == null || registrationId.isBlank()) {
            return;
        }

        realtimeNotificationService.removeListener(memberId, registrationId);
    }

    public String currentMemberId() {
        UUID memberId = SessionContext.getMemberId();
        return memberId == null ? null : memberId.toString();
    }

    public List<PendingRoleOfferOption> listPendingRoleOffers() {
        String token = SessionContext.getSessionToken();
        if (token == null) {
            return List.of();
        }
        try {
            return memberService.listPendingRoleOffers(token).stream()
                    .map(offer -> new PendingRoleOfferOption(
                            offer.getOfferId(),
                            offer.getCompanyName(),
                            offer.getRole()))
                    .toList();
        } catch (RuntimeException ex) {
            logger.warn("Failed to load pending role offers", ex);
            return List.of();
        }
    }

    public ActionResult respondToRoleOffer(UUID offerId, boolean accepted) {
        String token = SessionContext.getSessionToken();
        if (token == null) {
            return ActionResult.failure(LOGIN_REQUIRED_MESSAGE);
        }
        if (offerId == null) {
            return ActionResult.failure("Offer ID is required.");
        }
        try {
            companyService.respondToRoleAppointment(token, offerId, accepted);
            return ActionResult.success(accepted ? "Role offer accepted." : "Role offer rejected.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, "Could not respond to role offer. Please try again."));
        }
    }

    private String userMessage(RuntimeException ex, String fallback) {
        PresenterErrorClassifier.logFailure(logger, fallback, ex);
        return PresenterErrorClassifier.resolveUserMessage(ex, fallback);
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

    public record RegistrationResult(boolean success, String message, String memberId, String registrationId) {

        public static RegistrationResult success(String memberId, String registrationId) {
            return new RegistrationResult(true, "Real-time notifications connected.", memberId, registrationId);
        }

        public static RegistrationResult failure(String message, String memberId, String registrationId) {
            return new RegistrationResult(false, message, memberId, registrationId);
        }
    }
}
