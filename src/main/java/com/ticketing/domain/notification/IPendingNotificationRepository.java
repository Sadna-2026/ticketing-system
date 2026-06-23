package com.ticketing.domain.notification;

import java.util.List;

/**
 * Stores member notifications. Undelivered messages are flushed to a listener when the member
 * connects; delivered messages are retained (marked seen) so the Notifications tab can show the
 * member's history rather than dropping notifications once delivered (#490).
 */
public interface IPendingNotificationRepository {
    /** Saves an undelivered (unseen) notification. */
    void savePendingNotification(String userId, String message);

    /** Saves a notification, recording whether it has already been delivered to the member. */
    void savePendingNotification(String userId, String message, boolean seen);

    /** Undelivered (unseen) notifications, oldest first — what to flush to a newly connected listener. */
    List<String> getPendingNotifications(String userId);

    /** All notifications (delivered and not), oldest first — the member's full history inbox. */
    List<String> getNotificationHistory(String userId);

    /** Marks every unseen notification for the member as delivered, so it is not pushed again. */
    void markAllSeen(String userId);

    /** Removes all notifications for the member (history included). */
    void clearPendingNotifications(String userId);

    void deleteAll();
}

