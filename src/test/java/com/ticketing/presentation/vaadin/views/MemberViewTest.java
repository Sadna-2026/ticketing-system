package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.HasValueAndElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
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
    void GivenGuestSession_WhenRendered_ThenProfileFormIsHiddenWithLoginMessage() {
        SessionContext.setSessionToken("guest-token");

        MemberView view = new MemberView(mock(MemberPresenter.class));

        assertTrue(hasText(view, "You must be logged in to view your profile."));
        assertNull(findButton(view, "Save"));
        assertNull(findFieldValue(view, "Username"));
    }

    @Test
    void GivenLoggedInMember_WhenRendered_ThenCurrentMemberDetailsAreLoadedIntoFields() {
        memberSession();

        MemberView view = new MemberView(presenterReturning(MEMBER));

        assertEquals("alice", findFieldValue(view, "Username"));
        assertEquals("alice@example.com", findFieldValue(view, "Email"));
        assertEquals("050-1234567", findFieldValue(view, "Phone number"));
        assertEquals(LocalDate.of(1990, 1, 1), findDateValue(view, "Date of birth"));
    }

    @Test
    void GivenLoggedInMember_WhenRendered_ThenMandatoryIdentityFieldsShowRequiredIndicatorAndOptionalFieldsDoNot() {
        memberSession();

        MemberView view = new MemberView(presenterReturning(MEMBER));

        assertRequired(view, "Username");
        assertRequired(view, "Email");
        assertOptional(view, "Phone number");
        assertOptional(view, "Date of birth");
    }

    @Test
    void GivenEditedProfile_WhenSaveClicked_ThenPresenterIsCalledAndSuccessMessageIsShown() {
        memberSession();
        MemberPresenter presenter = presenterReturning(MEMBER);
        MemberDto updated = new MemberDto(
                MEMBER.memberId(),
                "alice-updated",
                "alice.updated@example.com",
                "050-7654321",
                LocalDate.of(1991, 2, 2)
        );
        when(presenter.getCurrentMember()).thenReturn(MEMBER, updated);
        when(presenter.updateIdentifyingDetails(
                "alice-updated",
                "alice.updated@example.com",
                "050-7654321",
                LocalDate.of(1991, 2, 2)
        )).thenReturn(new MemberPresenter.UpdateResult(true, "Profile updated successfully."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            MemberView view = new MemberView(presenter);
            setFieldValue(view, "Username", "alice-updated");
            setFieldValue(view, "Email", "alice.updated@example.com");
            setFieldValue(view, "Phone number", "050-7654321");
            setDateValue(view, "Date of birth", LocalDate.of(1991, 2, 2));

            clickButton(view, "Save");

            verify(presenter).updateIdentifyingDetails(
                    "alice-updated",
                    "alice.updated@example.com",
                    "050-7654321",
                    LocalDate.of(1991, 2, 2)
            );
            verify(presenter, times(2)).getCurrentMember();
            uiMessagesMock.verify(() -> UiMessages.success("Profile updated successfully."));
            assertEquals("alice-updated", findFieldValue(view, "Username"));
            assertEquals("alice.updated@example.com", findFieldValue(view, "Email"));
            assertFalse(((HasValidation) findTextField(view, "Username")).isInvalid());
            assertFalse(((HasValidation) findEmailField(view, "Email")).isInvalid());
        }
    }

    @Test
    void GivenUnchangedProfile_WhenSaveClicked_ThenShowsInfoMessageWithoutCallingPresenter() {
        memberSession();
        MemberPresenter presenter = presenterReturning(MEMBER);

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            MemberView view = new MemberView(presenter);
            clickButton(view, "Save");

            verify(presenter, never()).updateIdentifyingDetails(any(), any(), any(), any());
            uiMessagesMock.verify(() -> UiMessages.info("No changes to save."));
        }
    }

    @Test
    void GivenSaveClicked_WhenFailure_ThenShowsSpecificErrorMessage() {
        memberSession();
        MemberPresenter presenter = presenterReturning(MEMBER);
        when(presenter.updateIdentifyingDetails(any(), any(), any(), any()))
                .thenReturn(new MemberPresenter.UpdateResult(false, "Username or email already in use."));

        try (var uiMessagesMock = mockStatic(UiMessages.class)) {
            MemberView view = new MemberView(presenter);
            setFieldValue(view, "Username", "owner");
            clickButton(view, "Save");

            verify(presenter).updateIdentifyingDetails(
                    eq("owner"),
                    eq("alice@example.com"),
                    eq("050-1234567"),
                    eq(LocalDate.of(1990, 1, 1))
            );
            uiMessagesMock.verify(() -> UiMessages.error("Username or email already in use."));
        }
    }

    @Test
    void GivenProfileLoadFails_WhenRendered_ThenShowsErrorMessage() {
        memberSession();

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
        memberSession();
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

    private static void memberSession() {
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
    }

    private static boolean hasText(Component root, String expected) {
        return components(root).stream()
                .filter(com.vaadin.flow.component.HasText.class::isInstance)
                .map(com.vaadin.flow.component.HasText.class::cast)
                .anyMatch(component -> expected.equals(component.getText()));
    }

    private static List<Component> components(Component root) {
        List<Component> result = new ArrayList<>();
        collect(root, result);
        return result;
    }

    private static void collect(Component component, List<Component> result) {
        result.add(component);
        component.getChildren().forEach(child -> collect(child, result));
    }

    private static String findFieldValue(Component root, String label) {
        List<HasValueAndElement<?, ?>> fields = findFieldsByLabel(root, label);
        if (fields.isEmpty()) {
            return null;
        }
        Object value = fields.getFirst().getValue();
        return value == null ? null : String.valueOf(value);
    }

    private static LocalDate findDateValue(Component root, String label) {
        List<HasValueAndElement<?, ?>> fields = findFieldsByLabel(root, label);
        if (fields.isEmpty()) {
            return null;
        }
        return (LocalDate) fields.getFirst().getValue();
    }

    private static void setFieldValue(Component root, String label, String value) {
        if ("Email".equals(label)) {
            findEmailField(root, label).setValue(value);
            return;
        }
        findTextField(root, label).setValue(value);
    }

    private static void setDateValue(Component root, String label, LocalDate value) {
        findDatePicker(root, label).setValue(value);
    }

    private static TextField findTextField(Component root, String label) {
        return findFieldsByLabel(root, label).stream()
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("TextField not found: " + label));
    }

    private static EmailField findEmailField(Component root, String label) {
        return findFieldsByLabel(root, label).stream()
                .filter(EmailField.class::isInstance)
                .map(EmailField.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("EmailField not found: " + label));
    }

    private static DatePicker findDatePicker(Component root, String label) {
        return findFieldsByLabel(root, label).stream()
                .filter(DatePicker.class::isInstance)
                .map(DatePicker.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("DatePicker not found: " + label));
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
