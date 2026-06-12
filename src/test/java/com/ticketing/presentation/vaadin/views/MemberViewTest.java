package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.domain.member.MemberDto;
import com.ticketing.presentation.vaadin.presenters.MemberPresenter;
import com.ticketing.presentation.vaadin.testsupport.SynchronousUi;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasLabel;
import com.vaadin.flow.component.HasValueAndElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.router.BeforeEnterEvent;

@DisplayName("MemberView")
@ExtendWith(VaadinSessionExtension.class)
class MemberViewTest {

    private static final MemberDto MEMBER =
            new MemberDto(UUID.randomUUID(), "alice", "alice@example.com", "050-1234567", LocalDate.of(1990, 1, 1));

    private MemberPresenter presenterReturning(MemberDto member) {
        MemberPresenter presenter = mock(MemberPresenter.class);
        when(presenter.getCurrentMember()).thenReturn(member);
        return presenter;
    }

    @Test
    void GivenLoggedInMember_WhenRendered_ThenMandatoryIdentityFieldsShowRequiredIndicatorAndOptionalFieldsDoNot() {
        SessionContext.setMemberId(UUID.randomUUID());

        MemberView view = new MemberView(presenterReturning(MEMBER));

        assertRequired(view, "Username");
        assertRequired(view, "Email");
        assertOptional(view, "Phone number");
        assertOptional(view, "Date of birth");
    }

    @Test
    void GivenSaveClicked_WhenSuccess_ThenShowsSuccessMessage() {
        SessionContext.setMemberId(UUID.randomUUID());
        MemberPresenter presenter = presenterReturning(MEMBER);
        when(presenter.updateIdentifyingDetails(any(), any(), any(), any()))
                .thenReturn(new MemberPresenter.UpdateResult(true, "Profile updated."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            MemberView view = new MemberView(presenter);
            clickButton(view, "Save");

            uiMessagesMock.verify(() -> UiMessages.success("Profile updated."));
        }
    }

    @Test
    void GivenSaveClicked_WhenFailure_ThenShowsSpecificErrorMessage() {
        SessionContext.setMemberId(UUID.randomUUID());
        MemberPresenter presenter = presenterReturning(MEMBER);
        when(presenter.updateIdentifyingDetails(any(), any(), any(), any()))
                .thenReturn(new MemberPresenter.UpdateResult(false, "Email already in use."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            MemberView view = new MemberView(presenter);
            clickButton(view, "Save");

            uiMessagesMock.verify(() -> UiMessages.error("Email already in use."));
        }
    }

    @Test
    void GivenProfileLoadFails_WhenRendered_ThenShowsErrorMessage() {
        SessionContext.setMemberId(UUID.randomUUID());

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            new MemberView(presenterReturning(null));

            uiMessagesMock.verify(() -> UiMessages.error("Failed to load profile details."));
        }
    }

    @Test
    void GivenGuestSession_WhenEnteringProfile_ThenForwardedToHome() {
        MemberView view = new MemberView(mock(MemberPresenter.class));
        // Hold a strong reference: UI.getCurrent() is backed by a WeakReference, so an inline
        // SynchronousUi can be GC'd before beforeEnter runs (see VaadinSessionExtension javadoc).
        UI ui = SynchronousUi.create();
        UI.setCurrent(ui);
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);

        view.beforeEnter(event);

        verify(event).forwardTo(HomeView.class);
    }

    @Test
    void GivenMemberSession_WhenEnteringProfile_ThenNavigationIsAllowed() {
        SessionContext.setMemberId(UUID.randomUUID());
        MemberView view = new MemberView(presenterReturning(MEMBER));
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);

        view.beforeEnter(event);

        verify(event, never()).forwardTo(HomeView.class);
    }

    private void assertRequired(Component root, String label) {
        List<HasValueAndElement<?, ?>> fields = findFieldsByLabel(root, label);
        assertFalse(fields.isEmpty(), "No field found with label: " + label);
        fields.forEach(field -> assertTrue(field.isRequiredIndicatorVisible(),
                label + " should be marked required"));
    }

    private void assertOptional(Component root, String label) {
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
}
