package com.ticketing.application.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.domain.notification.IPendingNotificationRepository;

@org.springframework.stereotype.Service
public class NotificationQueryService {

    private final IPendingNotificationRepository pendingNotificationRepository;
    private static final Logger log = LoggerFactory.getLogger(NotificationQueryService.class);

    public NotificationQueryService(IPendingNotificationRepository pendingNotificationRepository) {
        this.pendingNotificationRepository = pendingNotificationRepository;
    }

    public List<String> getPendingNotifications(String memberId) {
        log.info("Fetching pending notifications for member: {}", memberId);
        List<String> result = pendingNotificationRepository.getPendingNotifications(memberId);
        log.info("Pending notifications completed: memberId={}, count={}", memberId, result.size());

        return result;
    }

    /** Full notification history (delivered + undelivered) for the member's Notifications inbox. */
    public List<String> getNotificationHistory(String memberId) {
        log.info("Fetching notification history for member: {}", memberId);
        List<String> result = pendingNotificationRepository.getNotificationHistory(memberId);
        log.info("Notification history completed: memberId={}, count={}", memberId, result.size());

        return result;
    }

    public void clearPendingNotifications(String memberId) {
        log.info("Clearing pending notifications for member: {}", memberId);

        pendingNotificationRepository.clearPendingNotifications(memberId);
        log.info("Clearing pending notifications completed: memberId={}", memberId);
    }
}

