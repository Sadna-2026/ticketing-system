package com.ticketing.presentation.vaadin.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.dom.Element;

/**
 * Helpers for view tests that exercise {@link com.ticketing.presentation.vaadin.util.DestructiveActionDialogs}.
 */
public final class ConfirmDialogTestSupport {

    private ConfirmDialogTestSupport() {
    }

    public static boolean isOpen() {
        return findOpenDialog().isPresent();
    }

    public static String openDialogText() {
        ConfirmDialog dialog = findOpenDialog()
                .orElseThrow(() -> new AssertionError("No open confirm dialog"));
        return dialog.getText();
    }

    public static void confirm() {
        clickDialogButton("Confirm");
    }

    public static void cancel() {
        clickDialogButton("Cancel");
    }

    public static Optional<ConfirmDialog> findOpenDialog() {
        UI ui = UI.getCurrent();
        if (ui == null) {
            return Optional.empty();
        }
        return findConfirmDialogs(ui).stream()
                .filter(ConfirmDialog::isOpened)
                .findFirst();
    }

    private static void clickDialogButton(String text) {
        ConfirmDialog dialog = findOpenDialog()
                .orElseThrow(() -> new AssertionError("No open confirm dialog"));
        Button button = findButtonInComponent(dialog, text);
        if (button == null && dialog.getFooter() != null) {
            button = findButtonInElement(dialog.getFooter().getElement(), text);
        }
        if (button == null) {
            throw new AssertionError("Dialog button not found: " + text);
        }
        button.click();
    }

    private static List<ConfirmDialog> findConfirmDialogs(Component root) {
        List<ConfirmDialog> dialogs = new ArrayList<>();
        if (root instanceof ConfirmDialog dialog) {
            dialogs.add(dialog);
        }
        root.getChildren().forEach(child -> dialogs.addAll(findConfirmDialogs(child)));
        return dialogs;
    }

    private static Button findButtonInComponent(Component root, String text) {
        if (root instanceof Button button && text.equals(button.getText())) {
            return button;
        }
        for (Component child : root.getChildren().toList()) {
            Button found = findButtonInComponent(child, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Button findButtonInElement(Element element, String text) {
        if ("button".equalsIgnoreCase(element.getTag().orElse(""))
                && text.equals(element.getText().orElse(""))) {
            return element.getComponent().map(Button.class::cast).orElse(null);
        }
        for (Element child : element.getChildren().toList()) {
            Button found = findButtonInElement(child, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
