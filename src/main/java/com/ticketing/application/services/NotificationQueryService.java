package com.ticketing.application.services;

import java.util.List;

import com.ticketing.domain.notification.IPendingNotificationRepository;

@org.springframework.stereotype.Service
public class NotificationQueryService {

    private final IPendingNotificationRepository pendingNotificationRepository;

    public NotificationQueryService(IPendingNotificationRepository pendingNotificationRepository) {
        this.pendingNotificationRepository = pendingNotificationRepository;
    }

    public List<String> getPendingNotifications(String memberId) {
        return pendingNotificationRepository.getPendingNotifications(memberId);
    }

    public void clearPendingNotifications(String memberId) {
        pendingNotificationRepository.clearPendingNotifications(memberId);
    }
}

