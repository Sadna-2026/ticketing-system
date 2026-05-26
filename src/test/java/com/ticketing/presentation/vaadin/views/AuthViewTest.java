package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.presentation.vaadin.presenters.AuthPresenter;
import com.ticketing.presentation.vaadin.presenters.AuthPresenter.AuthResult;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.PasswordField;

@DisplayName("AuthView")
class AuthViewTest {

    @Test
    void GivenAuthView_WhenRendered_ThenPasswordInputsUsePasswordField() {
        AuthPresenter presenter = mockPresenter(none());

        AuthView view = new AuthView(presenter);

        assertEquals(2, countComponents(view, PasswordField.class));
    }

    @Test
    void GivenNoSession_WhenRendered_ThenOnlyEnterGuestActionIsVisible() {
        AuthView view = new AuthView(mockPresenter(none()));

        assertTrue(hasVisibleButton(view, "Enter as guest"));
        assertFalse(hasVisibleButton(view, "Log in"));
        assertFalse(hasVisibleButton(view, "Register"));
        assertFalse(hasVisibleButton(view, "Log out"));
    }

    @Test
    void GivenGuestSession_WhenRendered_ThenLoginAndRegisterAreVisible() {
        AuthView view = new AuthView(mockPresenter(guest()));

        assertFalse(hasVisibleButton(view, "Enter as guest"));
        assertTrue(hasVisibleButton(view, "Log in"));
        assertTrue(hasVisibleButton(view, "Register"));
        assertFalse(hasVisibleButton(view, "Log out"));
    }

    @Test
    void GivenMemberSession_WhenRendered_ThenOnlyLogoutIsVisible() {
        AuthView view = new AuthView(mockPresenter(member()));

        assertFalse(hasVisibleButton(view, "Enter as guest"));
        assertFalse(hasVisibleButton(view, "Log in"));
        assertFalse(hasVisibleButton(view, "Register"));
        assertTrue(hasVisibleButton(view, "Log out"));
    }

    @Test
    void GivenEnterGuestClicked_WhenSessionChanges_ThenLoginAndRegisterBecomeVisible() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: none", "Current session: Guest");
        when(presenter.currentSessionState()).thenReturn(none(), guest());
        when(presenter.startGuestSession()).thenReturn(AuthResult.success("Guest session started."));

        AuthView view = new AuthView(presenter);
        clickButton(view, "Enter as guest");

        assertFalse(hasVisibleButton(view, "Enter as guest"));
        assertTrue(hasVisibleButton(view, "Log in"));
        assertTrue(hasVisibleButton(view, "Register"));
    }

    private AuthPresenter mockPresenter(SessionContext.UiState state) {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn(labelFor(state));
        when(presenter.currentSessionState()).thenReturn(state);
        return presenter;
    }

    private boolean hasVisibleButton(Component root, String text) {
        Button button = findButton(root, text);
        return button != null && isEffectivelyVisible(button);
    }

    private void clickButton(Component root, String text) {
        Button button = findButton(root, text);
        if (button == null) {
            throw new AssertionError("Button not found: " + text);
        }
        button.click();
    }

    private Button findButton(Component root, String text) {
        if (root instanceof Button button && text.equals(button.getText())) {
            return button;
        }
        return root.getChildren()
                .map(child -> findButton(child, text))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean isEffectivelyVisible(Component component) {
        if (!component.isVisible()) {
            return false;
        }
        return component.getParent()
                .map(this::isEffectivelyVisible)
                .orElse(true);
    }

    private long countComponents(Component root, Class<? extends Component> type) {
        long current = type.isInstance(root) ? 1 : 0;
        return current + root.getChildren()
                .mapToLong(child -> countComponents(child, type))
                .sum();
    }

    private static SessionContext.UiState none() {
        return new SessionContext.UiState(false, false, false, false, null, null);
    }

    private static SessionContext.UiState guest() {
        return new SessionContext.UiState(true, true, false, false, null, "Guest");
    }

    private static SessionContext.UiState member() {
        return new SessionContext.UiState(true, false, true, false, "alice", "Member");
    }

    private static String labelFor(SessionContext.UiState state) {
        if (state.loggedInMember()) {
            return "Current session: Member (" + state.username() + ")";
        }
        return state.guest() ? "Current session: Guest" : "Current session: none";
    }
}
