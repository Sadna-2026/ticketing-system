package com.ticketing.presentation.vaadin.util;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * Persistent banner for surfacing infrastructure-failure context that a toast
 * would miss (#517). Unlike {@link UiMessages#error(String)} which auto-dismisses
 * after a few seconds, this banner stays visible until the calling view explicitly
 * hides it — appropriate for "the database is down, your work is paused" where the
 * user needs continuous reassurance, not a fleeting popup.
 *
 * <p>Hidden by default; show with {@link #showError(String)}, hide with
 * {@link #hide()}. The implementation uses the Lumo error-color tokens so the styling
 * follows the rest of the app's theming.
 */
public final class ErrorBanner extends Div {

    private final Span message = new Span();
    private boolean shown;

    public ErrorBanner() {
        addClassName("error-banner");
        getStyle()
                .set("display", "none")
                .set("padding", "var(--lumo-space-m)")
                .set("margin-bottom", "var(--lumo-space-s)")
                .set("background-color", "var(--lumo-error-color)")
                .set("color", "var(--lumo-error-contrast-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("font-weight", "500")
                .set("box-shadow", "var(--lumo-box-shadow-s)");
        getElement().setAttribute("role", "alert");
        getElement().setAttribute("aria-live", "assertive");
        add(message);
    }

    /** Shows the banner with the given text. Idempotent — repeated calls just update the text. */
    public void showError(String text) {
        message.setText(text);
        getStyle().set("display", "block");
        this.shown = true;
    }

    /** Hides the banner. Idempotent. */
    public void hide() {
        getStyle().set("display", "none");
        this.shown = false;
    }

    /** True when the banner is currently visible to the user. */
    public boolean isShown() {
        return shown;
    }

    /** The text currently shown (empty string when {@link #isShown()} is false). */
    public String currentText() {
        return shown ? message.getText() : "";
    }
}
