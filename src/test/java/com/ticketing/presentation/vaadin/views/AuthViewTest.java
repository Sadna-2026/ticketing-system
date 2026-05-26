package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.presentation.vaadin.presenters.AuthPresenter;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.PasswordField;

@DisplayName("AuthView")
class AuthViewTest {

    @Test
    void GivenAuthView_WhenRendered_ThenPasswordInputsUsePasswordField() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: none");

        AuthView view = new AuthView(presenter);

        assertEquals(2, countComponents(view, PasswordField.class));
    }

    @Test
    void GivenAuthView_WhenRendered_ThenContainsGuestLoginRegisterAndLogoutActions() {
        AuthPresenter presenter = mock(AuthPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: none");

        AuthView view = new AuthView(presenter);

        assertTrue(hasButton(view, "Enter as guest"));
        assertTrue(hasButton(view, "Log in"));
        assertTrue(hasButton(view, "Register"));
        assertTrue(hasButton(view, "Log out"));
    }

    private boolean hasButton(Component root, String text) {
        if (root instanceof Button button && text.equals(button.getText())) {
            return true;
        }
        return root.getChildren().anyMatch(child -> hasButton(child, text));
    }

    private long countComponents(Component root, Class<? extends Component> type) {
        long current = type.isInstance(root) ? 1 : 0;
        return current + root.getChildren()
                .mapToLong(child -> countComponents(child, type))
                .sum();
    }
}
