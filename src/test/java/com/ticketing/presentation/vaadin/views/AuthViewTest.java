package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.presentation.vaadin.presenters.AuthPresenter;
import com.ticketing.presentation.vaadin.presenters.AuthPresenter.AuthResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasLabel;
import com.vaadin.flow.component.HasValueAndElement;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.PasswordField;

@DisplayName("AuthView")
@ExtendWith(VaadinSessionExtension.class)
class AuthViewTest {

    @Test
    void GivenAuthView_WhenRendered_ThenPasswordInputsUsePasswordField() {
        AuthPresenter presenter = mockPresenter(none());

        AuthView view = new AuthView(presenter);

        assertEquals(3, countComponents(view, PasswordField.class));
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
        assertTrue(hasVisibleButton(view, "Log out"));
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

    @Test
    void GivenEnterGuestClicked_WhenSuccess_ThenShowsSuccessMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: none", "Current session: Guest");
        when(presenter.currentSessionState()).thenReturn(none(), guest());
        when(presenter.startGuestSession()).thenReturn(AuthResult.success("Guest session started."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Enter as guest");

            uiMessagesMock.verify(() -> UiMessages.success("Guest session started."));
        }
    }

    @Test
    void GivenEnterGuestClicked_WhenFailure_ThenShowsErrorMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: none");
        when(presenter.currentSessionState()).thenReturn(none());
        when(presenter.startGuestSession()).thenReturn(AuthResult.failure("Could not start guest session."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Enter as guest");

            uiMessagesMock.verify(() -> UiMessages.error("Could not start guest session."));
        }
    }

    @Test
    void GivenLogoutClicked_WhenGuestSession_ThenSuccessMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Guest", "Current session: none");
        when(presenter.currentSessionState()).thenReturn(guest(), none());
        when(presenter.logout()).thenReturn(AuthResult.success("Guest session ended."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Log out");

            uiMessagesMock.verify(() -> UiMessages.success("Guest session ended."));
            assertFalse(hasVisibleButton(view, "Log out"));
            assertTrue(hasVisibleButton(view, "Enter as guest"));
        }
    }

    @Test
    void GivenLogoutClicked_WhenGuestSessionFails_ThenErrorMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Guest", "Current session: Guest");
        when(presenter.currentSessionState()).thenReturn(guest(), guest());
        when(presenter.logout()).thenReturn(AuthResult.failure("Failed to exit guest session."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Log out");

            uiMessagesMock.verify(() -> UiMessages.error("Failed to exit guest session."));
            assertTrue(hasVisibleButton(view, "Log out"));
            assertFalse(hasVisibleButton(view, "Enter as guest"));
        }
    }

    @Test
    void GivenLoginClicked_WhenSuccess_ThenShowsSuccessMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Guest");
        when(presenter.currentSessionState()).thenReturn(guest());
        when(presenter.login(anyString(), anyString()))
                .thenReturn(AuthResult.success("Login successful."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Log in");

            uiMessagesMock.verify(() -> UiMessages.success("Login successful."));
        }
    }

    @Test
    void GivenLoginClicked_WhenFailure_ThenShowsErrorMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Guest");
        when(presenter.currentSessionState()).thenReturn(guest());
        when(presenter.login(anyString(), anyString()))
                .thenReturn(AuthResult.failure("Login failed."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Log in");

            uiMessagesMock.verify(() -> UiMessages.error("Login failed."));
        }
    }

    @Test
    void GivenRegisterClicked_WhenSuccess_ThenShowsSuccessMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Guest");
        when(presenter.currentSessionState()).thenReturn(guest());
        when(presenter.register(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(AuthResult.success("Registration successful."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Register");

            uiMessagesMock.verify(() -> UiMessages.success("Registration successful."));
        }
    }

    @Test
    void GivenRegisterClicked_WhenFailure_ThenShowsErrorMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Guest");
        when(presenter.currentSessionState()).thenReturn(guest());
        when(presenter.register(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(AuthResult.failure("Registration failed."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Register");

            uiMessagesMock.verify(() -> UiMessages.error("Registration failed."));
        }
    }

    @Test
    void GivenAuthView_WhenRendered_ThenMandatoryFieldsShowRequiredIndicatorAndOptionalFieldsDoNot() {
        AuthView view = new AuthView(mockPresenter(guest()));

        assertAllRequired(view, "Username");
        assertAllRequired(view, "Password");
        assertAllRequired(view, "Admin username");
        assertAllRequired(view, "Admin password");
        assertAllRequired(view, "Email");

        assertNoneRequired(view, "Phone number");
        assertNoneRequired(view, "Date of birth");
    }

    private void assertAllRequired(Component root, String label) {
        List<HasValueAndElement<?, ?>> fields = findFieldsByLabel(root, label);
        assertFalse(fields.isEmpty(), "No field found with label: " + label);
        fields.forEach(field -> assertTrue(field.isRequiredIndicatorVisible(),
                label + " should be marked required"));
    }

    private void assertNoneRequired(Component root, String label) {
        List<HasValueAndElement<?, ?>> fields = findFieldsByLabel(root, label);
        assertFalse(fields.isEmpty(), "No field found with label: " + label);
        fields.forEach(field -> assertFalse(field.isRequiredIndicatorVisible(),
                label + " should be optional"));
    }

    private static List<HasValueAndElement<?, ?>> findFieldsByLabel(Component root, String label) {
        List<HasValueAndElement<?, ?>> result = new ArrayList<>();
        collectFieldsByLabel(root, label, result);
        return result;
    }

    private static void collectFieldsByLabel(Component component, String label,
            List<HasValueAndElement<?, ?>> out) {
        if (component instanceof HasLabel hasLabel && label.equals(hasLabel.getLabel())
                && component instanceof HasValueAndElement<?, ?> field) {
            out.add(field);
        }
        component.getChildren().forEach(child -> collectFieldsByLabel(child, label, out));
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
