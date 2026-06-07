package com.ticketing.presentation.vaadin.util;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;
import com.vaadin.flow.component.notification.NotificationVariant;

public final class UiMessages {

    private UiMessages() {
    }

    public static void success(String message) {
        show(message, 4000, NotificationVariant.LUMO_SUCCESS);
    }

    public static void error(String message) {
        show(message, 6000, NotificationVariant.LUMO_ERROR);
    }

    public static void info(String message) {
        if (UI.getCurrent() == null) {
            return;
        }
        Notification.show(message, 4000, Position.TOP_CENTER);
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

        Notification notification = Notification.show(message, durationMillis, Position.TOP_CENTER);
        notification.addThemeVariants(variant);

        if (variant == NotificationVariant.LUMO_ERROR) {
            ComponentUtil.setData(ui, "lastErrorNotification", notification);
        }
    }
}
