package com.ticketing.presentation.vaadin.views;

import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.AuthPresenter;
import com.ticketing.presentation.vaadin.presenters.AuthPresenter.AuthResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "auth", layout = MainLayout.class)
@PageTitle("Authentication")
public class AuthView extends VerticalLayout {

    private final AuthPresenter presenter;
    private final Span sessionStatus = new Span();

    public AuthView(AuthPresenter presenter) {
        this.presenter = presenter;

        setPadding(true);
        setSpacing(true);

        add(
                new H2("Authentication"),
                new Paragraph("Start as a guest, then log in or register to continue as a member."),
                sessionStatus,
                guestSection(),
                loginSection(),
                registerSection(),
                logoutSection()
        );
        refreshSessionStatus();
    }

    private HorizontalLayout guestSection() {
        Button startGuestSession = new Button("Enter as guest", event -> {
            handle(presenter.startGuestSession());
            refreshSessionStatus();
        });

        HorizontalLayout layout = new HorizontalLayout(startGuestSession);
        layout.setAlignItems(Alignment.BASELINE);
        return layout;
    }

    private VerticalLayout loginSection() {
        TextField username = new TextField("Username");
        PasswordField password = new PasswordField("Password");
        Button login = new Button("Log in", event -> {
            handle(presenter.login(username.getValue(), password.getValue()));
            refreshSessionStatus();
        });

        FormLayout form = new FormLayout(username, password);
        VerticalLayout layout = new VerticalLayout(new H3("Log in"), form, login);
        layout.setPadding(false);
        return layout;
    }

    private VerticalLayout registerSection() {
        TextField username = new TextField("Username");
        EmailField email = new EmailField("Email");
        PasswordField password = new PasswordField("Password");
        TextField phoneNumber = new TextField("Phone number");
        DatePicker dateOfBirth = new DatePicker("Date of birth");
        Button register = new Button("Register", event -> {
            handle(presenter.register(
                    username.getValue(),
                    email.getValue(),
                    password.getValue(),
                    phoneNumber.getValue(),
                    dateOfBirth.getValue()
            ));
            refreshSessionStatus();
        });

        FormLayout form = new FormLayout(username, email, password, phoneNumber, dateOfBirth);
        VerticalLayout layout = new VerticalLayout(new H3("Register"), form, register);
        layout.setPadding(false);
        return layout;
    }

    private HorizontalLayout logoutSection() {
        Button logout = new Button("Log out", event -> {
            handle(presenter.logout());
            refreshSessionStatus();
        });

        HorizontalLayout layout = new HorizontalLayout(logout);
        layout.setAlignItems(Alignment.BASELINE);
        return layout;
    }

    private void handle(AuthResult result) {
        if (result.success()) {
            UiMessages.success(result.message());
        } else {
            UiMessages.error(result.message());
        }
    }

    private void refreshSessionStatus() {
        sessionStatus.setText(presenter.currentSessionLabel());
    }
}
