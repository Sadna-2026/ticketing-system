package com.ticketing.presentation.vaadin.util;

import java.util.ArrayDeque;
import java.util.Queue;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.notification.NotificationVariant;

public final class UiMessages {

    private static final String INFO_QUEUE_KEY = "infoNotificationQueue";

    private UiMessages() {
    }

    public static void success(String message) {
        show(message, 4000, NotificationVariant.LUMO_SUCCESS);
    }

    public static void error(String message) {
        show(message, 6000, NotificationVariant.LUMO_ERROR);
    }

    /**
     * Shows an info toast. Rapid-fire calls are queued and shown sequentially so that
     * simultaneous notifications (e.g. "checkout complete" + "event sold out") do not
     * overlap visually.
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
            showNextInfo(ui, queue);
        }
    }

    private static void showNextInfo(UI ui, Queue<String> queue) {
        String message = queue.peek();
        if (message == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setDuration(4000);
        notification.setPosition(Position.TOP_CENTER);
        Span content = new Span(message);
        content.getStyle().set("white-space", "pre-line");
        notification.add(content);
        notification.addOpenedChangeListener(e -> {
            if (!e.isOpened()) {
                queue.poll();
                if (!queue.isEmpty()) {
                    ui.access(() -> showNextInfo(ui, queue));
                }
            }
        });
        notification.open();
    }

    /**
     * Shows a notification with the specified message, duration, and theme variant.
     * 
     * For error notifications, this method implements an anti-stacking mechanism:
     * if an error notification is already being displayed on the current UI, it
     * will be
     * immediately closed before the new one is shown. This prevents multiple error
     * popups
     * from stacking on the screen when a user triggers them rapidly.
     *
     * @param message        the text to display
     * @param durationMillis the duration in milliseconds to show the notification
     * @param variant        the theme variant to apply (e.g., LUMO_ERROR,
     *                       LUMO_SUCCESS)
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
