package com.ticketing.presentation.vaadin.util;

import com.vaadin.flow.component.confirmdialog.ConfirmDialog;

/**
 * Shared confirmation flow for irreversible or high-impact UI actions (UX-6).
 */
public final class DestructiveActionDialogs {

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

        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(capitalize(verb) + "?");
        dialog.setText("Are you sure you want to " + verb + " \"" + target + "\"? This cannot be undone.");
        dialog.setCancelable(true);
        dialog.setCancelText("Cancel");
        dialog.setConfirmText("Confirm");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> onConfirm.run());
        dialog.open();
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
}
