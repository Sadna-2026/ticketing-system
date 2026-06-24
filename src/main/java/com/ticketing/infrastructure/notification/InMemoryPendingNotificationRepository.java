package com.ticketing.infrastructure.notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ticketing.domain.notification.IPendingNotificationRepository;

/**
 * Thread-safe in-memory implementation of IPendingNotificationRepository.
 * Retains delivered notifications (marked seen) so the Notifications tab can show history;
 * undelivered (unseen) ones are what gets flushed to a newly connected listener (#490).
 */
@Component
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "memory", matchIfMissing = true)
public class InMemoryPendingNotificationRepository implements IPendingNotificationRepository {

    /** A stored notification and whether it has already been delivered to the member. */
    private static final class Entry {
        final String message;
        boolean seen;

        Entry(String message, boolean seen) {
            this.message = message;
            this.seen = seen;
        }
    }

    private final ConcurrentHashMap<String, List<Entry>> store = new ConcurrentHashMap<>();

    @Override
    public void savePendingNotification(String userId, String message) {
        savePendingNotification(userId, message, false);
    }

    @Override
    public void savePendingNotification(String userId, String message, boolean seen) {
        if (userId == null || message == null) {
            throw new IllegalArgumentException("userId and message must not be null");
        }
        List<Entry> entries = store.computeIfAbsent(userId, key -> Collections.synchronizedList(new ArrayList<>()));
        entries.add(new Entry(message, seen));
    }

    @Override
    public List<String> getPendingNotifications(String userId) {
        return messages(userId, true);
    }

    @Override
    public List<String> getNotificationHistory(String userId) {
        return messages(userId, false);
    }

    private List<String> messages(String userId, boolean unseenOnly) {
        if (userId == null) return Collections.emptyList();
        List<Entry> entries = store.get(userId);
        if (entries == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        synchronized (entries) {
            for (Entry entry : entries) {
                if (!unseenOnly || !entry.seen) {
                    result.add(entry.message);
                }
            }
        }
        return result;
    }

    @Override
    public void markAllSeen(String userId) {
        if (userId == null) return;
        List<Entry> entries = store.get(userId);
        if (entries == null) return;
        synchronized (entries) {
            for (Entry entry : entries) {
                entry.seen = true;
            }
        }
    }

    @Override
    public void clearPendingNotifications(String userId) {
        if (userId == null) return;
        store.remove(userId);
    }

    @Override
    public void deleteAll() {
        store.clear();
    }
}
