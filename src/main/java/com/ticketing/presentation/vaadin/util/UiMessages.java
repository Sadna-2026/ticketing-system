package com.ticketing.presentation.vaadin.util;

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

    private static void show(String message, int durationMillis, NotificationVariant variant) {
        if (UI.getCurrent() == null) {
            return;
        }
        Notification notification = Notification.show(message, durationMillis, Position.TOP_CENTER);
        notification.addThemeVariants(variant);
    }
}
