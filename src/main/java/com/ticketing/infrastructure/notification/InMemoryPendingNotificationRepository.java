package com.ticketing.infrastructure.notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.stereotype.Component;

import com.ticketing.domain.notification.IPendingNotificationRepository;


/**
 * Thread-safe in-memory implementation of IPendingNotificationRepository.
 * Stores pending notifications for users who are offline at the time of dispatch.
 */
@Component
public class InMemoryPendingNotificationRepository implements IPendingNotificationRepository {

    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<String>> store = new ConcurrentHashMap<>();

    @Override
    public void savePendingNotification(String userId, String message) {
        if (userId == null || message == null) {
            throw new IllegalArgumentException("userId and message must not be null");
        }
        store.computeIfAbsent(userId, key -> new ConcurrentLinkedQueue<>()).add(message);
    }

    @Override
    public List<String> getPendingNotifications(String userId) {
        if (userId == null) return Collections.emptyList();
        ConcurrentLinkedQueue<String> pending = store.get(userId);
        if (pending == null) return Collections.emptyList();
        return new ArrayList<>(pending);
    }

    @Override
    public void clearPendingNotifications(String userId) {
        if (userId == null) return;
        store.remove(userId);
    }
}
