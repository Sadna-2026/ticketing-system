package com.ticketing.infrastructure.notification;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import com.ticketing.domain.member.IPendingNotificationRepository;

/**
 * No-op stub implementation for V1.
 * Satisfies DI requirements without implementing persistence.
 */
public class StubPendingNotificationRepository implements IPendingNotificationRepository {
    
    private static final Logger logger = Logger.getLogger(StubPendingNotificationRepository.class.getName());

    @Override
    public void savePendingNotification(String userId, String message) {
        logger.info("STUB EVENT [Deferred to V2]: Would save pending notification for user [" + userId + "] -> " + message);
    }

    @Override
    public List<String> getPendingNotifications(String userId) {
        return Collections.emptyList();
    }

    @Override
    public void clearPendingNotifications(String userId) {
        // No-op for V1
    }
}
