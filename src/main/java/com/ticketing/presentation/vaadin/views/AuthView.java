package com.ticketing.presentation.vaadin.views;

import static com.ticketing.presentation.vaadin.util.RequiredFields.markRequired;

import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.AuthPresenter;
import com.ticketing.presentation.vaadin.presenters.AuthPresenter.AuthResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
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
    private final HorizontalLayout guestActions;
    private final VerticalLayout loginForm;
    private final VerticalLayout adminLoginForm;
    private final VerticalLayout registerForm;
    private final HorizontalLayout logoutActions;

    public AuthView(AuthPresenter presenter) {
        this.presenter = presenter;
        this.guestActions = guestSection();
        this.loginForm = loginSection();
        this.adminLoginForm = adminLoginSection();
        this.registerForm = registerSection();
        this.logoutActions = logoutSection();

        setPadding(true);
        setSpacing(true);

        add(
                new H2("Authentication"),
                new Paragraph("Start as a guest, then log in or register to continue as a member. Admins log in through the separate admin form."),
                sessionStatus,
                guestActions,
                loginForm,
                adminLoginForm,
                registerForm,
                logoutActions
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
        markRequired(username, "Username is required.");
        markRequired(password, "Password is required.");
        Button login = new Button("Log in", event -> {
            handle(presenter.login(username.getValue(), password.getValue()));
            refreshSessionStatus();
            MainLayout.refreshCurrentNavigation();
        });

        FormLayout form = new FormLayout(username, password);
        VerticalLayout layout = new VerticalLayout(new H3("Log in"), form, login);
        layout.setPadding(false);
        return layout;
    }

    private VerticalLayout adminLoginSection() {
        TextField username = new TextField("Admin username");
        PasswordField password = new PasswordField("Admin password");
        markRequired(username, "Admin username is required.");
        markRequired(password, "Admin password is required.");
        Button login = new Button("Log in as admin", event -> {
            handle(presenter.adminLogin(username.getValue(), password.getValue()));
            refreshSessionStatus();
            MainLayout.refreshCurrentNavigation();
        });

        FormLayout form = new FormLayout(username, password);
        VerticalLayout layout = new VerticalLayout(
                new H3("Admin login"),
                new Paragraph("For platform administrators only. Regular members should use the member login above."),
                form,
                login
        );
        layout.setPadding(false);
        return layout;
    }

    private VerticalLayout registerSection() {
        TextField username = new TextField("Username");
        EmailField email = new EmailField("Email");
        PasswordField password = new PasswordField("Password");
        TextField phoneNumber = new TextField("Phone number");
        DatePicker dateOfBirth = new DatePicker("Date of birth");
        markRequired(username, "Username is required.");
        markRequired(email, "Email is required.");
        markRequired(password, "Password is required.");
        Button register = new Button("Register", event -> {
            handle(presenter.register(
                    username.getValue(),
                    email.getValue(),
                    password.getValue(),
                    phoneNumber.getValue(),
                    dateOfBirth.getValue()
            ));
            refreshSessionStatus();
            MainLayout.refreshCurrentNavigation();
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
            MainLayout.refreshCurrentNavigation();
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
        var session = presenter.currentSessionState();
        guestActions.setVisible(session.noSession());
        loginForm.setVisible(session.guest());
        adminLoginForm.setVisible(session.guest());
        registerForm.setVisible(session.guest());
        logoutActions.setVisible(session.loggedInMember());
    }
}
