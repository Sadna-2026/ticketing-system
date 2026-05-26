package com.ticketing.infrastructure.notification;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ticketing.application.services.INotificationService;
import com.ticketing.domain.notification.IPendingNotificationRepository;

import jakarta.annotation.PreDestroy;

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
@Component
public class WebSocketNotificationService implements INotificationService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketNotificationService.class);

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, NotificationListener>> listeners = new ConcurrentHashMap<>();
    private final IPendingNotificationRepository pendingRepository;
    private final ExecutorService executor;

    @Autowired
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

        ConcurrentHashMap<String, NotificationListener> memberListeners = listeners.get(memberId);
        if (memberListeners == null || memberListeners.isEmpty()) {
            pendingRepository.savePendingNotification(memberId, message);
            log.info("User offline, notification saved as pending: memberId={}", memberId);
            return;
        }

        executor.submit(() -> {
            boolean delivered = false;
            for (NotificationListener listener : memberListeners.values()) {
                try {
                    listener.onMessage(message);
                    delivered = true;
                    log.info("Notification pushed to connected user: memberId={}", memberId);
                } catch (Exception e) {
                    log.error("Failed to push notification to memberId={}", memberId, e);
                }
            }
            if (!delivered) {
                pendingRepository.savePendingNotification(memberId, message);
                log.info("No active listener accepted notification, saved as pending: memberId={}", memberId);
            }
        });
    }

    /**
     * Registers a listener for a user (e.g. when a WebSocket session opens).
     * Any pending notifications accumulated while the user was offline are
     * flushed immediately to the new listener.
     */
    public String registerListener(String memberId, NotificationListener listener) {
        if (memberId == null || listener == null) {
            throw new IllegalArgumentException("memberId and listener must not be null");
        }
        String registrationId = UUID.randomUUID().toString();
        listeners.computeIfAbsent(memberId, key -> new ConcurrentHashMap<>())
                .put(registrationId, listener);
        log.info("Listener registered for memberId={}, registrationId={}", memberId, registrationId);

        flushPendingNotifications(memberId, listener);
        return registrationId;
    }

    /**
     * Removes all listeners for a user.
     */
    public void removeListener(String memberId) {
        if (memberId == null) return;
        listeners.remove(memberId);
        log.info("Listeners removed for memberId={}", memberId);
    }

    /**
     * Removes a specific listener registration for a user.
     */
    public void removeListener(String memberId, String registrationId) {
        if (memberId == null || registrationId == null) return;
        ConcurrentHashMap<String, NotificationListener> memberListeners = listeners.get(memberId);
        if (memberListeners == null) return;

        memberListeners.remove(registrationId);
        if (memberListeners.isEmpty()) {
            listeners.remove(memberId, memberListeners);
        }
        log.info("Listener removed for memberId={}, registrationId={}", memberId, registrationId);
    }

    /**
     * Returns true if a listener is currently registered for the given memberId.
     */
    public boolean hasListener(String memberId) {
        ConcurrentHashMap<String, NotificationListener> memberListeners = memberId == null ? null : listeners.get(memberId);
        return memberListeners != null && !memberListeners.isEmpty();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
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

