package com.ticketing.application.services;

/**
 * Minimal interface for V1 to decouple other services from notification logic.
 * Full real-time delivery implementation is deferred to V2.
 */
public interface INotificationService {
    void notify(String memberId, String message);
}

