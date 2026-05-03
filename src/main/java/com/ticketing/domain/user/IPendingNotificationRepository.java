package com.ticketing.domain.user;

import java.util.List;

/**
 * Placeholder interface for V1 (UC-I.6).
 * Full persistence and delivery on login for delayed notifications is deferred to V2.
 */
public interface IPendingNotificationRepository {
    void savePendingNotification(String userId, String message);
    List<String> getPendingNotifications(String userId);
    void clearPendingNotifications(String userId);
}
