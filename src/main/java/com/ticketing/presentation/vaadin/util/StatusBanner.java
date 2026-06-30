package com.ticketing.presentation.vaadin.util;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * Full-width fixed banner for prominent infrastructure status (database down / restored).
 * Stays visible until explicitly hidden or replaced.
 */
public final class StatusBanner extends Div {

    private final Span message = new Span();
    private boolean shown;

    public StatusBanner() {
        addClassName("status-banner");
        setWidthFull();
        getStyle()
                .set("display", "none")
                .set("position", "fixed")
                .set("top", "0")
                .set("left", "0")
                .set("right", "0")
                .set("z-index", "2000")
                .set("padding", "var(--lumo-space-m)")
                .set("font-weight", "600")
                .set("text-align", "center")
                .set("box-shadow", "var(--lumo-box-shadow-m)");
        getElement().setAttribute("role", "alert");
        getElement().setAttribute("aria-live", "assertive");
        add(message);
    }

    public void showError(String text) {
        applyStyle("var(--lumo-error-color)", "var(--lumo-error-contrast-color)");
        message.setText(text);
        getStyle().set("display", "block");
        shown = true;
    }

    public void showSuccess(String text) {
        applyStyle("var(--lumo-success-color)", "var(--lumo-success-contrast-color)");
        message.setText(text);
        getStyle().set("display", "block");
        shown = true;
    }

    public void hide() {
        getStyle().set("display", "none");
        shown = false;
    }

    public boolean isShown() {
        return shown;
    }

    private void applyStyle(String background, String foreground) {
        getStyle()
                .set("background-color", background)
                .set("color", foreground);
    }
}
