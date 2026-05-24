package com.ticketing.infrastructure.notification;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.services.INotificationService;
import com.ticketing.domain.notification.IPendingNotificationRepository;

/**
 * Real notification service backed by per-user listeners (e.g. WebSocket sessions).
 *
 * Connected user   → pushed immediately via the registered NotificationListener.
 * Disconnected user → message is saved to IPendingNotificationRepository for
 *                     delivery when the user reconnects (see {@link #registerListener}).
 *
 * Application and domain layers remain unaware of WebSockets — they only see
 * INotificationService.notify(memberId, message).
 */
public class WebSocketNotificationService implements INotificationService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketNotificationService.class);

    private final ConcurrentHashMap<String, NotificationListener> listeners = new ConcurrentHashMap<>();
    private final IPendingNotificationRepository pendingRepository;
    private final ExecutorService executor;

    public WebSocketNotificationService(IPendingNotificationRepository pendingRepository) {
        this(pendingRepository, Executors.newCachedThreadPool());
    }

    public WebSocketNotificationService(IPendingNotificationRepository pendingRepository,
                                        ExecutorService executor) {
        if (pendingRepository == null) throw new IllegalArgumentException("pendingRepository is required");
        if (executor == null) throw new IllegalArgumentException("executor is required");
        this.pendingRepository = pendingRepository;
        this.executor = executor;
    }

    @Override
    public void notify(String memberId, String message) {
        if (memberId == null || message == null) {
            log.warn("Ignoring notification with null memberId or message");
            return;
        }

        NotificationListener listener = listeners.get(memberId);
        if (listener != null) {
            executor.submit(() -> {
                try {
                    listener.onMessage(message);
                    log.info("Notification pushed to connected user: memberId={}", memberId);
                } catch (Exception e) {
                    log.error("Failed to push notification to memberId={}, saving as pending", memberId, e);
                    pendingRepository.savePendingNotification(memberId, message);
                }
            });
        } else {
            pendingRepository.savePendingNotification(memberId, message);
            log.info("User offline, notification saved as pending: memberId={}", memberId);
        }
    }

    /**
     * Registers a listener for a user (e.g. when a WebSocket session opens).
     * Any pending notifications accumulated while the user was offline are
     * flushed immediately to the new listener.
     */
    public void registerListener(String memberId, NotificationListener listener) {
        if (memberId == null || listener == null) {
            throw new IllegalArgumentException("memberId and listener must not be null");
        }
        listeners.put(memberId, listener);
        log.info("Listener registered for memberId={}", memberId);

        flushPendingNotifications(memberId, listener);
    }

    /**
     * Removes the listener for a user (e.g. when a WebSocket session closes).
     */
    public void removeListener(String memberId) {
        if (memberId == null) return;
        listeners.remove(memberId);
        log.info("Listener removed for memberId={}", memberId);
    }

    /**
     * Returns true if a listener is currently registered for the given memberId.
     */
    public boolean hasListener(String memberId) {
        return memberId != null && listeners.containsKey(memberId);
    }

    private void flushPendingNotifications(String memberId, NotificationListener listener) {
        List<String> pending = pendingRepository.getPendingNotifications(memberId);
        if (pending.isEmpty()) return;

        log.info("Flushing {} pending notifications for memberId={}", pending.size(), memberId);
        for (String message : pending) {
            try {
                listener.onMessage(message);
            } catch (Exception e) {
                log.error("Failed to flush pending notification for memberId={}", memberId, e);
                return;
            }
        }
        pendingRepository.clearPendingNotifications(memberId);
    }
}
