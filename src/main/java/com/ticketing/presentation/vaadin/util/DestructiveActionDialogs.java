package com.ticketing.presentation.vaadin.util;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;

/**
 * Shared confirmation flow for high-impact UI actions (UX-6).
 * Each action uses a tailored header, body, and confirm-button style.
 */
public final class DestructiveActionDialogs {

    private static TestBridge testBridge;

    private DestructiveActionDialogs() {
    }

    public enum Tone {
        DESTRUCTIVE("error primary", "Confirm"),
        RESTORATIVE("primary", "Restore access");

        private final String confirmTheme;
        private final String confirmText;

        Tone(String confirmTheme, String confirmText) {
            this.confirmTheme = confirmTheme;
            this.confirmText = confirmText;
        }
    }

    public record Prompt(String header, String message, Tone tone) {
    }

    public static void show(Prompt prompt, Runnable onConfirm) {
        if (testBridge != null) {
            testBridge.stage(prompt, onConfirm);
            return;
        }
        openVaadinDialog(prompt, onConfirm);
    }

    public static void confirmClearCart(String eventName, Runnable onConfirm) {
        show(new Prompt(
                "Clear cart?",
                areYouSure("clear the cart for \"" + normalizeTarget(eventName) + "\"")
                        + " This releases all reserved tickets back.",
                Tone.DESTRUCTIVE),
                onConfirm);
    }

    public static void confirmRemoveOrderItem(String itemLabel, Runnable onConfirm) {
        show(new Prompt(
                "Remove order item?",
                areYouSure("remove \"" + normalizeTarget(itemLabel) + "\" from your cart")
                        + " This releases all reserved tickets back.",
                Tone.DESTRUCTIVE),
                onConfirm);
    }

    public static void confirmDecreaseGaCapacity(String zoneLabel, Runnable onConfirm) {
        show(new Prompt(
                "Decrease GA capacity?",
                areYouSure("decrease GA capacity for \"" + normalizeTarget(zoneLabel) + "\"")
                        + " This releases all reserved tickets back.",
                Tone.DESTRUCTIVE),
                onConfirm);
    }

    public static void confirmRemoveSeat(String seatLabel, Runnable onConfirm) {
        show(plainPrompt("Remove seat?", "remove seat", seatLabel), onConfirm);
    }

    public static void confirmRejectRoleOffer(String offerLabel, Runnable onConfirm) {
        show(plainPrompt("Reject role offer?", "reject the role offer", offerLabel), onConfirm);
    }

    public static void confirmRevokePersonnel(String username, Runnable onConfirm) {
        show(plainPrompt("Revoke personnel?", "revoke personnel", username), onConfirm);
    }

    public static void confirmRemovePurchasePolicy(String targetLabel, Runnable onConfirm) {
        show(plainPrompt(
                "Remove purchase policy?",
                "remove the purchase policy for",
                targetLabel),
                onConfirm);
    }

    public static void confirmRemoveDiscountPolicy(String targetLabel, Runnable onConfirm) {
        show(plainPrompt(
                "Remove discount policy?",
                "remove the discount policy for",
                targetLabel),
                onConfirm);
    }

    public static void confirmSuspendCompany(String companyName, Runnable onConfirm) {
        show(new Prompt(
                "Suspend company?",
                areYouSure("suspend \"" + normalizeTarget(companyName) + "\"")
                        + " You can reopen the company later.",
                Tone.DESTRUCTIVE),
                onConfirm);
    }

    public static void confirmSuspendMember(String username, Runnable onConfirm) {
        show(new Prompt(
                "Suspend member?",
                areYouSure("suspend \"" + normalizeTarget(username) + "\"")
                        + " You can cancel the suspension later.",
                Tone.DESTRUCTIVE),
                onConfirm);
    }

    public static void confirmCancelSuspension(String username, Runnable onConfirm) {
        show(new Prompt(
                "Restore member access?",
                "Restore access for \"" + normalizeTarget(username) + "\"? This will end the active suspension.",
                Tone.RESTORATIVE),
                onConfirm);
    }

    public static void confirmRemoveMember(String username, Runnable onConfirm) {
        show(new Prompt(
                "Remove member?",
                areYouSure("remove \"" + normalizeTarget(username) + "\"")
                        + " This permanently deletes the account.",
                Tone.DESTRUCTIVE),
                onConfirm);
    }

    public static void confirmCloseCompany(String companyName, Runnable onConfirm) {
        show(new Prompt(
                "Close company?",
                areYouSure("permanently close \"" + normalizeTarget(companyName) + "\"")
                        + " This permanently closes the company and revokes staff appointments.",
                Tone.DESTRUCTIVE),
                onConfirm);
    }

    public static void confirmCancelEvent(String eventName, Runnable onConfirm) {
        show(new Prompt(
                "Cancel event?",
                areYouSure("cancel \"" + normalizeTarget(eventName) + "\"")
                        + " This cancels the event and refunds purchases.",
                Tone.DESTRUCTIVE),
                onConfirm);
    }

    public static void confirmRelinquishOwnership(String companyName, Runnable onConfirm) {
        show(new Prompt(
                "Relinquish ownership?",
                "Leave \"" + normalizeTarget(companyName) + "\" as an owner?"
                        + " You will no longer be an owner of this company.",
                Tone.DESTRUCTIVE),
                onConfirm);
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

    /** The staged confirmation header in test mode. */
    public static String pendingConfirmationHeader() {
        if (testBridge == null) {
            return "";
        }
        return testBridge.header;
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

    private static Prompt plainPrompt(String header, String verbPhrase, String targetName) {
        return new Prompt(
                header,
                areYouSure(verbPhrase + " \"" + normalizeTarget(targetName) + "\""),
                Tone.DESTRUCTIVE);
    }

    private static String areYouSure(String actionPhrase) {
        return "Are you sure you want to " + actionPhrase + "?";
    }

    private static void openVaadinDialog(Prompt prompt, Runnable onConfirm) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(prompt.header());
        dialog.setText(prompt.message());
        dialog.setCancelable(true);
        dialog.setCancelText("Cancel");
        dialog.setConfirmText(prompt.tone().confirmText);
        dialog.setConfirmButtonTheme(prompt.tone().confirmTheme);
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

    private static String normalizeTarget(String targetName) {
        String normalized = targetName == null ? "" : targetName.trim();
        return normalized.isEmpty() ? "the selected item" : normalized;
    }

    private static final class TestBridge {
        private String header = "";
        private String message = "";
        private Runnable pendingConfirm;

        private void stage(Prompt prompt, Runnable onConfirm) {
            this.header = prompt.header();
            this.message = prompt.message();
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
