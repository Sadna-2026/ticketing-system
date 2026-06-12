package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.presentation.vaadin.presenters.AuthPresenter;
import com.ticketing.presentation.vaadin.presenters.AuthPresenter.AuthResult;
import com.ticketing.presentation.vaadin.testsupport.SynchronousUi;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasLabel;
import com.vaadin.flow.component.HasValueAndElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;

@DisplayName("AuthView")
@ExtendWith(VaadinSessionExtension.class)
class AuthViewTest {

    // ── Password field usage ───────────────────────────────────────────────────

    @Test
    void GivenAuthView_WhenRendered_ThenPasswordInputsUsePasswordField() {
        AuthView view = new AuthView(mockPresenter(none()));

        // Login + Register + Admin login each have a password field → 3 total
        assertEquals(3, countComponents(view, PasswordField.class));
    }

    // ── Visibility: no-session state ───────────────────────────────────────────

    @Test
    void GivenNoSession_WhenRendered_ThenTabSheetIsVisibleAndLogoutIsHidden() {
        AuthView view = new AuthView(mockPresenter(none()));

        // All four tabs (and their content) are reachable via the tab sheet
        assertTrue(isTabSheetVisible(view));
        assertFalse(hasVisibleButton(view, "Log out"));
    }

    @Test
    void GivenNoSession_WhenRendered_ThenAllFourActionButtonsArePresent() {
        AuthView view = new AuthView(mockPresenter(none()));

        assertTrue(hasButton(view, "Log in"));
        assertTrue(hasButton(view, "Register"));
        assertTrue(hasButton(view, "Continue as guest"));
        assertTrue(hasButton(view, "Log in as admin"));
    }

    // ── Visibility: guest state ────────────────────────────────────────────────

    @Test
    void GivenGuestSession_WhenRendered_ThenTabSheetVisibleAndLogoutVisible() {
        AuthView view = new AuthView(mockPresenter(guest()));

        assertTrue(isTabSheetVisible(view));
        assertTrue(hasVisibleButton(view, "Log out"));
    }

    // ── Visibility: member state ───────────────────────────────────────────────

    @Test
    void GivenMemberSession_WhenRendered_ThenTabSheetIsHiddenAndOnlyLogoutIsVisible() {
        AuthView view = new AuthView(mockPresenter(member()));

        assertFalse(isTabSheetVisible(view));
        assertTrue(hasVisibleButton(view, "Log out"));
    }

    // ── Guest entry ────────────────────────────────────────────────────────────

    @Test
    void GivenEnterGuestClicked_WhenSuccess_ThenShowsSuccessMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: none", "Current session: Guest");
        when(presenter.currentSessionState()).thenReturn(none(), guest());
        when(presenter.startGuestSession()).thenReturn(AuthResult.success("Guest session started."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Continue as guest");

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
            clickButton(view, "Continue as guest");

            uiMessagesMock.verify(() -> UiMessages.error("Could not start guest session."));
        }
    }

    // ── Login ──────────────────────────────────────────────────────────────────

    @Test
    void GivenLoginClicked_WhenSuccess_ThenShowsSuccessMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: none");
        when(presenter.currentSessionState()).thenReturn(none());
        when(presenter.login(anyString(), anyString()))
                .thenReturn(AuthResult.success("Member logged in successfully."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Log in");

            uiMessagesMock.verify(() -> UiMessages.success("Member logged in successfully."));
        }
    }

    @Test
    void GivenLoginClicked_WhenFailure_ThenShowsErrorMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: none");
        when(presenter.currentSessionState()).thenReturn(none());
        when(presenter.login(anyString(), anyString()))
                .thenReturn(AuthResult.failure("Login failed."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Log in");

            uiMessagesMock.verify(() -> UiMessages.error("Login failed."));
        }
    }

    // ── Register ───────────────────────────────────────────────────────────────

    @Test
    void GivenRegisterClicked_WhenSuccess_ThenShowsSuccessMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: none");
        when(presenter.currentSessionState()).thenReturn(none());
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
        when(presenter.currentSessionLabel()).thenReturn("Current session: none");
        when(presenter.currentSessionState()).thenReturn(none());
        when(presenter.register(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(AuthResult.failure("Registration failed."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Register");

            uiMessagesMock.verify(() -> UiMessages.error("Registration failed."));
        }
    }

    // ── Admin login ────────────────────────────────────────────────────────────

    @Test
    void GivenAdminLoginClicked_WhenSuccess_ThenShowsSuccessMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: none");
        when(presenter.currentSessionState()).thenReturn(none());
        when(presenter.adminLogin(anyString(), anyString()))
                .thenReturn(AuthResult.success("Admin logged in."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Log in as admin");

            uiMessagesMock.verify(() -> UiMessages.success("Admin logged in."));
        }
    }

    @Test
    void GivenAdminLoginClicked_WhenFailure_ThenShowsErrorMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: none");
        when(presenter.currentSessionState()).thenReturn(none());
        when(presenter.adminLogin(anyString(), anyString()))
                .thenReturn(AuthResult.failure("Invalid admin credentials."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Log in as admin");

            uiMessagesMock.verify(() -> UiMessages.error("Invalid admin credentials."));
        }
    }

    // ── Logout ─────────────────────────────────────────────────────────────────

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
            // After logout, tab sheet is visible again (no-session state)
            assertTrue(isTabSheetVisible(view));
            assertFalse(hasVisibleButton(view, "Log out"));
        }
    }

    @Test
    void GivenLogoutClicked_WhenGuestSessionFails_ThenErrorMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Guest");
        when(presenter.currentSessionState()).thenReturn(guest());
        when(presenter.logout()).thenReturn(AuthResult.failure("Failed to exit guest session."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Log out");

            uiMessagesMock.verify(() -> UiMessages.error("Failed to exit guest session."));
            assertTrue(hasVisibleButton(view, "Log out"));
        }
    }

    @Test
    void GivenLogoutClicked_WhenMemberSession_ThenSuccessMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn(
                "Current session: Member (alice)", "Current session: none");
        when(presenter.currentSessionState()).thenReturn(member(), none());
        when(presenter.logout()).thenReturn(AuthResult.success("Logged out successfully."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Log out");

            uiMessagesMock.verify(() -> UiMessages.success("Logged out successfully."));
            // After logout the UI returns to no-session state: tab sheet visible, logout hidden.
            assertTrue(isTabSheetVisible(view));
            assertFalse(hasVisibleButton(view, "Log out"));
        }
    }

    @Test
    void GivenLogoutClicked_WhenMemberSessionFails_ThenErrorMessage() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Member (alice)");
        when(presenter.currentSessionState()).thenReturn(member());
        when(presenter.logout()).thenReturn(AuthResult.failure("Logout failed. Please try again."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            AuthView view = new AuthView(presenter);
            clickButton(view, "Log out");

            uiMessagesMock.verify(() -> UiMessages.error("Logout failed. Please try again."));
            // Session unchanged: logout button must still be visible.
            assertTrue(hasVisibleButton(view, "Log out"));
        }
    }

    @Test
    void GivenLogoutClicked_WhenSuccessful_ThenAllInputFieldsAreCleared() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Member (alice)", "Current session: none");
        when(presenter.currentSessionState()).thenReturn(member(), none());
        when(presenter.logout()).thenReturn(AuthResult.success("Logged out successfully."));

        AuthView view = new AuthView(presenter);

        List<com.vaadin.flow.component.textfield.TextField> textFields = findAll(view, com.vaadin.flow.component.textfield.TextField.class);
        textFields.forEach(f -> f.setValue("dummy text"));

        List<com.vaadin.flow.component.textfield.PasswordField> passFields = findAll(view, com.vaadin.flow.component.textfield.PasswordField.class);
        passFields.forEach(f -> f.setValue("secret"));

        List<com.vaadin.flow.component.textfield.EmailField> emailFields = findAll(view, com.vaadin.flow.component.textfield.EmailField.class);
        emailFields.forEach(f -> f.setValue("dummy@example.com"));

        assertFalse(textFields.isEmpty());
        assertFalse(passFields.isEmpty());
        assertFalse(emailFields.isEmpty());

        clickButton(view, "Log out");

        textFields.forEach(f -> assertTrue(f.isEmpty(), "TextField should be cleared"));
        passFields.forEach(f -> assertTrue(f.isEmpty(), "PasswordField should be cleared"));
        emailFields.forEach(f -> assertTrue(f.isEmpty(), "EmailField should be cleared"));
    }

    // ── Required field indicators ──────────────────────────────────────────────

    @Test
    void GivenAuthView_WhenRendered_ThenMandatoryFieldsShowRequiredIndicatorAndOptionalFieldsDoNot() {
        AuthView view = new AuthView(mockPresenter(none()));

        assertAllRequired(view, "Username");
        assertAllRequired(view, "Password");
        assertAllRequired(view, "Admin username");
        assertAllRequired(view, "Admin password");
        assertAllRequired(view, "Email");

        assertNoneRequired(view, "Phone number");
        assertNoneRequired(view, "Date of birth");
    }

    // ── HomeView navigation guard (FIX-V2-13) ────────────────────────────────

    @Test
    void GivenNoSession_WhenNavigatingToRoot_ThenForwardedToAuthView() {
        HomeView view = new HomeView();
        // Hold a strong reference so the UI is not GC'd before beforeEnter runs.
        UI ui = SynchronousUi.create();
        UI.setCurrent(ui);
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);

        view.beforeEnter(event);

        verify(event).forwardTo(AuthView.class);
    }

    @Test
    void GivenGuestSession_WhenNavigatingToRoot_ThenNavigationIsAllowed() {
        SessionContext.setSessionToken("guest-token");
        HomeView view = new HomeView();
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);

        view.beforeEnter(event);

        verify(event, never()).forwardTo(AuthView.class);
        SessionContext.clear();
    }

    // ── Helper assertions ──────────────────────────────────────────────────────

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

    /** True if the Tabs navigation bar is present and visible (and thus the tab content). */
    private boolean isTabSheetVisible(Component root) {
        Tabs tabsComponent = findFirst(root, Tabs.class);
        return tabsComponent != null && tabsComponent.isVisible();
    }

    /** True if any Button with the given text is present (regardless of visibility). */
    private boolean hasButton(Component root, String text) {
        return findButton(root, text) != null;
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

    private <T extends Component> T findFirst(Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        return root.getChildren()
                .map(child -> findFirst(child, type))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private <T extends Component> List<T> findAll(Component root, Class<T> type) {
        List<T> result = new ArrayList<>();
        collectAll(root, type, result);
        return result;
    }

    private <T extends Component> void collectAll(Component root, Class<T> type, List<T> out) {
        if (type.isInstance(root)) {
            out.add(type.cast(root));
        }
        root.getChildren().forEach(child -> collectAll(child, type, out));
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
