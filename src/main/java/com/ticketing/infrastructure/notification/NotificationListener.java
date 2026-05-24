package com.ticketing.infrastructure.notification;

/**
 * Callback interface for delivering real-time notifications to a connected user.
 * Infrastructure-only — application and domain layers are unaware of this type.
 */
@FunctionalInterface
public interface NotificationListener {
    void onMessage(String message);
}

