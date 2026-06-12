package com.ticketing.presentation.vaadin.views;

import com.ticketing.domain.member.MemberDto;
import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.MemberPresenter;
import com.ticketing.presentation.vaadin.util.RequiredFields;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
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

@Route(value = "profile", layout = MainLayout.class)
@PageTitle("Profile")
public class MemberView extends VerticalLayout implements BeforeEnterObserver {

    private final MemberPresenter presenter;

    private final TextField username = new TextField("Username");
    private final EmailField email = new EmailField("Email");
    private final TextField phoneNumber = new TextField("Phone number");
    private final DatePicker dateOfBirth = new DatePicker("Date of birth");

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

        // Inline validation for the mandatory identity fields (UX-7); phone and date of
        // birth stay optional, consistent with registration.
        RequiredFields.markRequired(username, "Username is required.");
        RequiredFields.markRequired(email, "Email is required.");

        FormLayout form = new FormLayout(username, email, phoneNumber, dateOfBirth);
        
        VerticalLayout layout = new VerticalLayout(new H3("Edit Identifying Details"), form, saveButton);
        layout.setPadding(false);

        add(layout);
    }

    private void loadMemberData() {
        MemberDto member = presenter.getCurrentMember();
        if (member != null) {
            username.setValue(member.username() != null ? member.username() : "");
            email.setValue(member.email() != null ? member.email() : "");
            phoneNumber.setValue(member.phoneNumber() != null ? member.phoneNumber() : "");
            dateOfBirth.setValue(member.dateOfBirth());
        } else {
            UiMessages.error("Failed to load profile details.");
        }
    }

    private void saveProfile() {
        MemberPresenter.UpdateResult result = presenter.updateIdentifyingDetails(
                username.getValue(),
                email.getValue(),
                phoneNumber.getValue(),
                dateOfBirth.getValue()
        );

        if (result.success()) {
            UiMessages.success(result.message());
            MainLayout.refreshCurrentNavigation();
        } else {
            UiMessages.error(result.message());
        }
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