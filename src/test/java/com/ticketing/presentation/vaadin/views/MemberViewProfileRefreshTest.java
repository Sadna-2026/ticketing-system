package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.domain.member.MemberDto;
import com.ticketing.presentation.vaadin.presenters.MemberPresenter;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;

/**
 * Regression test: the profile view is {@code @UIScope} and survives a client-side login/logout
 * navigation, so it must reload the current member on every entry (in {@code beforeEnter}) rather
 * than caching a constructor-time snapshot. Otherwise, logging out and back in as a different user
 * shows the previous user's username/email/date-of-birth.
 */
@DisplayName("MemberView refreshes profile per navigation")
@ExtendWith(VaadinSessionExtension.class)
class MemberViewProfileRefreshTest {

    @Test
    void givenUserSwitch_whenReEnteringProfile_thenFieldsShowCurrentMember() throws Exception {
        SessionContext.setSessionToken("token");
        SessionContext.setMemberId(UUID.randomUUID());

        MemberDto userOne = new MemberDto(UUID.randomUUID(), "u1", "u1@example.com", null, null);
        MemberDto userThree = new MemberDto(
                UUID.randomUUID(), "u3", "u3@example.com", "050-000-0003", LocalDate.of(2000, 1, 1));

        MemberPresenter presenter = mock(MemberPresenter.class);
        when(presenter.getCurrentMember()).thenReturn(userOne);

        MemberView view = new MemberView(presenter);
        BeforeEnterEvent event = mock(BeforeEnterEvent.class);

        // Enter as u1.
        view.beforeEnter(event);
        assertEquals("u1", text(view, "username").getValue());
        assertEquals("u1@example.com", email(view, "email").getValue());
        assertEquals(null, date(view, "dateOfBirth").getValue());

        // Simulate logging out and back in as u3, then re-entering the same UI-scoped view.
        when(presenter.getCurrentMember()).thenReturn(userThree);
        view.beforeEnter(event);

        assertEquals("u3", text(view, "username").getValue(), "profile must reflect the current user, not a stale one");
        assertEquals("u3@example.com", email(view, "email").getValue());
        assertEquals(LocalDate.of(2000, 1, 1), date(view, "dateOfBirth").getValue(), "u3's date of birth must show");
    }

    private static TextField text(MemberView view, String name) throws Exception {
        return (TextField) field(view, name);
    }

    private static EmailField email(MemberView view, String name) throws Exception {
        return (EmailField) field(view, name);
    }

    private static DatePicker date(MemberView view, String name) throws Exception {
        return (DatePicker) field(view, name);
    }

    private static Object field(MemberView view, String name) throws Exception {
        Field field = MemberView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(view);
    }
}
