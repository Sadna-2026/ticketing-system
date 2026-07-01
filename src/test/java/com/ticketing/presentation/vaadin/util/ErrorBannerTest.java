package com.ticketing.presentation.vaadin.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-level checks for the #517 persistent banner — show/hide invariants and
 * the basic styling/accessibility hooks the view relies on.
 */
@DisplayName("ErrorBanner (#517)")
class ErrorBannerTest {

    @Test
    void hiddenByDefault() {
        ErrorBanner banner = new ErrorBanner();

        assertThat(banner.isShown()).isFalse();
        assertThat(banner.currentText()).isEmpty();
        assertThat(banner.getStyle().get("display")).isEqualTo("none");
    }

    @Test
    void showError_marksShownAndStoresText() {
        ErrorBanner banner = new ErrorBanner();

        banner.showError("The database is down.");

        assertThat(banner.isShown()).isTrue();
        assertThat(banner.currentText()).isEqualTo("The database is down.");
        assertThat(banner.getStyle().get("display")).isEqualTo("block");
    }

    @Test
    void showError_isIdempotentAndUpdatesText() {
        ErrorBanner banner = new ErrorBanner();

        banner.showError("first message");
        banner.showError("second message");

        assertThat(banner.isShown()).isTrue();
        assertThat(banner.currentText()).isEqualTo("second message");
    }

    @Test
    void hideAfterShow_clearsVisibleState() {
        ErrorBanner banner = new ErrorBanner();

        banner.showError("temporary");
        banner.hide();

        assertThat(banner.isShown()).isFalse();
        assertThat(banner.currentText()).isEmpty();
        assertThat(banner.getStyle().get("display")).isEqualTo("none");
    }

    @Test
    void hideWhenAlreadyHidden_isNoop() {
        ErrorBanner banner = new ErrorBanner();

        banner.hide();
        banner.hide();

        assertThat(banner.isShown()).isFalse();
    }

    @Test
    void hasAccessibilityRoleAndLiveRegionForScreenReaders() {
        // Stays-visible banner is announced to screen readers on appearance —
        // role=alert + aria-live=assertive together give that behaviour with the
        // major screen-reader engines.
        ErrorBanner banner = new ErrorBanner();

        assertThat(banner.getElement().getAttribute("role")).isEqualTo("alert");
        assertThat(banner.getElement().getAttribute("aria-live")).isEqualTo("assertive");
    }
}
