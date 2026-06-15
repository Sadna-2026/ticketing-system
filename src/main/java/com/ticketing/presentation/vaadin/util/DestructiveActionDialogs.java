package com.ticketing.presentation.vaadin.util;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;

/**
 * Shared confirmation flow for irreversible or high-impact UI actions (UX-6).
 */
public final class DestructiveActionDialogs {

    private static TestBridge testBridge;

    private DestructiveActionDialogs() {
    }

    /**
     * Prompts for explicit confirmation before running a destructive action.
     *
     * @param actionVerb short verb phrase, e.g. {@code "remove member"}
     * @param targetName human-readable target (username, company name, event name)
     * @param onConfirm  runs only when the user confirms
     */
    public static void confirm(String actionVerb, String targetName, Runnable onConfirm) {
        String verb = normalize(actionVerb);
        String target = normalizeTarget(targetName);
        String message = buildMessage(verb, target);

        if (testBridge != null) {
            testBridge.stage(verb, target, message, onConfirm);
            return;
        }

        openVaadinDialog(verb, message, onConfirm);
    }

    /** Installs an in-memory confirmation bridge for view unit tests. */
    public static void installTestBridge() {
        testBridge = new TestBridge();
    }

    /** Removes the test bridge installed by {@link #installTestBridge()}. */
    public static void resetTestBridge() {
        testBridge = null;
    }

    /** Whether a destructive action is waiting for explicit confirmation in test mode. */
    public static boolean hasPendingConfirmation() {
        return testBridge != null && testBridge.pendingConfirm != null;
    }

    /** The staged confirmation message in test mode. */
    public static String pendingConfirmationMessage() {
        if (testBridge == null) {
            return "";
        }
        return testBridge.message;
    }

    /** Confirms the staged destructive action in test mode. */
    public static void confirmPending() {
        requireTestBridge().confirmPending();
    }

    /** Cancels the staged destructive action in test mode. */
    public static void cancelPending() {
        requireTestBridge().cancelPending();
    }

    private static TestBridge requireTestBridge() {
        if (testBridge == null) {
            throw new IllegalStateException("Test bridge is not installed");
        }
        return testBridge;
    }

    private static void openVaadinDialog(String verb, String message, Runnable onConfirm) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(capitalize(verb) + "?");
        dialog.setText(message);
        dialog.setCancelable(true);
        dialog.setCancelText("Cancel");
        dialog.setConfirmText("Confirm");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> {
            dialog.close();
            onConfirm.run();
        });
        dialog.addCancelListener(event -> dialog.close());

        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.add(dialog);
        }
        dialog.open();
    }

    private static String buildMessage(String verb, String target) {
        return "Are you sure you want to " + verb + " \"" + target + "\"? This cannot be undone.";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeTarget(String targetName) {
        String normalized = normalize(targetName);
        return normalized.isEmpty() ? "the selected item" : normalized;
    }

    private static String capitalize(String text) {
        if (text.isEmpty()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static final class TestBridge {
        private String message = "";
        private Runnable pendingConfirm;

        private void stage(String verb, String target, String stagedMessage, Runnable onConfirm) {
            this.message = stagedMessage;
            this.pendingConfirm = onConfirm;
        }

        private void confirmPending() {
            Runnable action = pendingConfirm;
            pendingConfirm = null;
            if (action != null) {
                action.run();
            }
        }

        private void cancelPending() {
            pendingConfirm = null;
        }
    }
}
