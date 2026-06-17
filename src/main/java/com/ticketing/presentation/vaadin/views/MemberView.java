package com.ticketing.presentation.vaadin.views;

import java.util.Objects;

import com.ticketing.domain.member.MemberDto;
import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.MemberPresenter;
import com.ticketing.presentation.vaadin.util.RequiredFields;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

@Route(value = "profile", layout = MainLayout.class)
@PageTitle("Profile")
@SpringComponent
@UIScope
public class MemberView extends VerticalLayout implements BeforeEnterObserver {

    private final MemberPresenter presenter;

    private static final String NO_CHANGES_MESSAGE = "No changes to save.";

    private final TextField username = new TextField("Username");
    private final EmailField email = new EmailField("Email");
    private final TextField phoneNumber = new TextField("Phone number");
    private final DatePicker dateOfBirth = new DatePicker("Date of birth");

    private MemberDto loadedProfile;

    public MemberView(MemberPresenter presenter) {
        this.presenter = presenter;

        setPadding(true);
        setSpacing(true);

        add(new H2("My Profile"));

        if (!SessionContext.isLoggedInMember()) {
            add(new Span("You must be logged in to view your profile."));
            return;
        }

        buildForm();
        loadMemberData();
    }

    private void buildForm() {
        Button saveButton = new Button("Save", event -> saveProfile());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        // Inline validation for the mandatory identity fields (UX-7); phone and date of
        // birth stay optional, consistent with registration.
        RequiredFields.markRequired(username, "Username is required.");
        RequiredFields.markRequired(email, "Email is required.");

        FormLayout form = new FormLayout(username, email, phoneNumber, dateOfBirth);
        
        VerticalLayout layout = new VerticalLayout(new H3("Edit Identifying Details"), form, saveButton);
        layout.setPadding(false);
        layout.setMaxWidth("560px");
        layout.addClassName("app-card");

        add(layout);
    }

    private void loadMemberData() {
        MemberDto member = presenter.getCurrentMember();
        if (member != null) {
            loadedProfile = member;
            username.setValue(member.username() != null ? member.username() : "");
            email.setValue(member.email() != null ? member.email() : "");
            phoneNumber.setValue(member.phoneNumber() != null ? member.phoneNumber() : "");
            dateOfBirth.setValue(member.dateOfBirth());
        } else {
            loadedProfile = null;
            UiMessages.error("Failed to load profile details.");
        }
    }

    private void saveProfile() {
        if (profileUnchanged()) {
            UiMessages.info(NO_CHANGES_MESSAGE);
            return;
        }

        MemberPresenter.UpdateResult result = presenter.updateIdentifyingDetails(
                username.getValue(),
                email.getValue(),
                phoneNumber.getValue(),
                dateOfBirth.getValue()
        );

        if (result.success()) {
            UiMessages.success(result.message());
            MainLayout.refreshCurrentNavigation();
            loadMemberData();
            username.setInvalid(false);
            email.setInvalid(false);
        } else {
            UiMessages.error(result.message());
        }
    }

    private boolean profileUnchanged() {
        if (loadedProfile == null) {
            return false;
        }
        return normalizeText(username.getValue()).equals(normalizeText(loadedProfile.username()))
                && normalizeText(email.getValue()).equals(normalizeText(loadedProfile.email()))
                && normalizeText(phoneNumber.getValue()).equals(normalizeText(loadedProfile.phoneNumber()))
                && Objects.equals(dateOfBirth.getValue(), loadedProfile.dateOfBirth());
    }

    private static String normalizeText(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!SessionContext.isLoggedInMember()) {
            event.forwardTo(HomeView.class);
            UI.getCurrent().access(() ->
                    UiMessages.error("You cannot access the profile page as a guest. Please log in first."));
        }
    }
}