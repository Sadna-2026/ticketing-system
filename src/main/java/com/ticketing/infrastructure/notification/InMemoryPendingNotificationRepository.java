package com.ticketing.infrastructure.notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.ticketing.domain.notification.IPendingNotificationRepository;


/**
 * Thread-safe in-memory implementation of IPendingNotificationRepository.
 * Stores pending notifications for users who are offline at the time of dispatch.
 */
@Component
public class InMemoryPendingNotificationRepository implements IPendingNotificationRepository {

    private final ConcurrentHashMap<String, List<String>> store = new ConcurrentHashMap<>();

    @Override
    public void savePendingNotification(String userId, String message) {
        if (userId == null || message == null) {
            throw new IllegalArgumentException("userId and message must not be null");
        }
        store.compute(userId, (key, existing) -> {
            List<String> list = (existing != null) ? existing : new ArrayList<>();
            list.add(message);
            return list;
        });
    }

    @Override
    public List<String> getPendingNotifications(String userId) {
        if (userId == null) return Collections.emptyList();
        List<String> pending = store.get(userId);
        if (pending == null) return Collections.emptyList();
        synchronized (pending) {
            return new ArrayList<>(pending);
        }
    }

    @Override
    public void clearPendingNotifications(String userId) {
        if (userId == null) return;
        store.remove(userId);
    }
}
