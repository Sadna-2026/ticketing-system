package com.ticketing.presentation.vaadin.util;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.notification.NotificationVariant;

public final class UiMessages {

    private static final int INFO_DURATION_MS = 4000;
    private static final String INFO_QUEUE_KEY = "infoNotificationQueue";

    // Single background thread advances the per-UI notification queue after each toast closes.
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ui-notification-scheduler");
                t.setDaemon(true);
                return t;
            });

    private UiMessages() {
    }

    public static void success(String message) {
        show(message, INFO_DURATION_MS, NotificationVariant.LUMO_SUCCESS);
    }

    public static void error(String message) {
        show(message, 6000, NotificationVariant.LUMO_ERROR);
    }

    /**
     * Shows an info toast. Rapid-fire calls are queued and shown sequentially — each one
     * waits for the previous to finish — so simultaneous notifications (e.g. "checkout
     * complete" + "event sold out") are never stacked on top of each other.
     */
    public static void info(String message) {
        UI ui = UI.getCurrent();
        if (ui == null) {
            return;
        }
        enqueueInfo(ui, message);
    }

    @SuppressWarnings("unchecked")
    private static void enqueueInfo(UI ui, String message) {
        Queue<String> queue = (Queue<String>) ComponentUtil.getData(ui, INFO_QUEUE_KEY);
        if (queue == null) {
            queue = new ArrayDeque<>();
            ComponentUtil.setData(ui, INFO_QUEUE_KEY, queue);
        }
        queue.add(message);
        if (queue.size() == 1) {
            // No toast is running — start immediately.
            showNextInfo(ui, queue);
        }
        // Otherwise the scheduler will advance the queue after the current toast closes.
    }

    private static void showNextInfo(UI ui, Queue<String> queue) {
        String message = queue.peek();
        if (message == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setDuration(INFO_DURATION_MS);
        notification.setPosition(Position.TOP_CENTER);
        Span content = new Span(message);
        content.getStyle().set("white-space", "pre-line");
        notification.add(content);
        notification.open();

        // Advance to the next queued message after this toast finishes.
        // Using a scheduler rather than addOpenedChangeListener because the auto-close
        // event does not reliably propagate from the client back to the server.
        SCHEDULER.schedule(() -> {
            queue.poll();
            if (!queue.isEmpty()) {
                ui.access(() -> showNextInfo(ui, queue));
            }
        }, INFO_DURATION_MS + 300L, TimeUnit.MILLISECONDS);
    }

    /**
     * Shows a notification with the specified message, duration, and theme variant.
     *
     * For error notifications, this method implements an anti-stacking mechanism:
     * if an error notification is already being displayed on the current UI, it
     * will be immediately closed before the new one is shown. This prevents multiple
     * error popups from stacking on the screen when a user triggers them rapidly.
     *
     * @param message        the text to display
     * @param durationMillis the duration in milliseconds to show the notification
     * @param variant        the theme variant to apply (e.g., LUMO_ERROR, LUMO_SUCCESS)
     */
    private static void show(String message, int durationMillis, NotificationVariant variant) {
        UI ui = UI.getCurrent();
        if (ui == null) {
            return;
        }

        if (variant == NotificationVariant.LUMO_ERROR) {
            Notification lastError = (Notification) ComponentUtil.getData(ui, "lastErrorNotification");
            if (lastError != null) {
                lastError.close();
            }
        }

        Notification notification = new Notification();
        notification.setDuration(durationMillis);
        notification.setPosition(Position.TOP_CENTER);
        Span content = new Span(message == null ? "" : message);
        content.getStyle().set("white-space", "pre-line");
        notification.add(content);
        notification.addThemeVariants(variant);
        notification.open();

        if (variant == NotificationVariant.LUMO_ERROR) {
            ComponentUtil.setData(ui, "lastErrorNotification", notification);
        }
    }
}
