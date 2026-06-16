package com.ticketing.presentation.vaadin.testsupport;

import com.ticketing.presentation.vaadin.util.DestructiveActionDialogs;

/**
 * Helpers for view tests that exercise {@link DestructiveActionDialogs}.
 */
public final class ConfirmDialogTestSupport {

    private ConfirmDialogTestSupport() {
    }

    public static void install() {
        DestructiveActionDialogs.installTestBridge();
    }

    public static void reset() {
        DestructiveActionDialogs.resetTestBridge();
    }

    public static boolean isOpen() {
        return DestructiveActionDialogs.hasPendingConfirmation();
    }

    public static String openDialogText() {
        return DestructiveActionDialogs.pendingConfirmationMessage();
    }

    public static String openDialogHeader() {
        return DestructiveActionDialogs.pendingConfirmationHeader();
    }

    public static void confirm() {
        DestructiveActionDialogs.confirmPending();
    }

    public static void cancel() {
        DestructiveActionDialogs.cancelPending();
    }
}
