package com.ticketing.presentation.vaadin.views;

import static com.ticketing.presentation.vaadin.util.RequiredFields.markRequired;

import java.util.HashMap;
import java.util.Map;

import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.AuthPresenter;
import com.ticketing.presentation.vaadin.presenters.AuthPresenter.AuthResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

@Route(value = "auth", layout = MainLayout.class)
@PageTitle("Authentication")
@SpringComponent
@UIScope
public class AuthView extends VerticalLayout {

    private final AuthPresenter presenter;
    private final Span sessionStatus = new Span();

    private final Tabs tabs = new Tabs();
    private final VerticalLayout tabContent = new VerticalLayout();
    private final Map<Tab, VerticalLayout> panelByTab = new HashMap<>();

    private final Tab loginTab      = new Tab("Log in");
    private final Tab registerTab   = new Tab("Register");
    private final Tab guestTab      = new Tab("Continue as guest");
    private final Tab adminTab      = new Tab("Admin login");

    private final HorizontalLayout logoutActions;

    public AuthView(AuthPresenter presenter) {
        this.presenter = presenter;
        this.logoutActions = logoutSection();

        setPadding(true);
        setSpacing(true);
        addClassName("app-auth");

        buildTabs();

        H2 heading = new H2("Welcome");
        heading.addClassName("app-auth-title");

        Div card = new Div(heading, sessionStatus, tabs, tabContent, logoutActions);
        card.addClassName("app-auth-card");
        add(card);
        refreshSessionStatus();
    }

    // ── Tab construction ───────────────────────────────────────────────────────

    private void buildTabs() {
        panelByTab.put(loginTab,    loginSection());
        panelByTab.put(registerTab, registerSection());
        panelByTab.put(guestTab,    guestSection());
        panelByTab.put(adminTab,    adminLoginSection());

        tabs.add(loginTab, registerTab, guestTab, adminTab);
        tabs.setWidthFull();

        // All panels live in the DOM; only the selected one is shown.
        for (VerticalLayout panel : panelByTab.values()) {
            panel.setVisible(false);
            tabContent.add(panel);
        }
        tabContent.setPadding(false);

        tabs.addSelectedChangeListener(e -> showPanel(e.getSelectedTab()));
        // Show the first tab by default.
        showPanel(loginTab);
    }

    private void showPanel(Tab selected) {
        panelByTab.forEach((tab, panel) -> panel.setVisible(tab.equals(selected)));
    }

    // ── Login tab ──────────────────────────────────────────────────────────────

    private VerticalLayout loginSection() {
        TextField username = new TextField("Username");
        PasswordField password = new PasswordField("Password");
        markRequired(username, "Username is required.");
        markRequired(password, "Password is required.");

        Button login = new Button("Log in", event -> {
            AuthResult result = presenter.login(username.getValue(), password.getValue());
            handle(result);
            refreshSessionStatus();
            MainLayout.refreshCurrentNavigation();
            if (result.success()) {
                password.clear();
                getUI().ifPresent(ui -> ui.navigate(HomeView.class));
            }
        });
        login.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout form = new FormLayout(username, password);
        VerticalLayout layout = new VerticalLayout(new H3("Log in"), form, login);
        layout.setPadding(false);
        return layout;
    }

    // ── Register tab ───────────────────────────────────────────────────────────

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
            AuthResult result = presenter.register(
                    username.getValue(),
                    email.getValue(),
                    password.getValue(),
                    phoneNumber.getValue(),
                    dateOfBirth.getValue()
            );
            handle(result);
            refreshSessionStatus();
            MainLayout.refreshCurrentNavigation();
            if (result.success()) {
                password.clear();
                getUI().ifPresent(ui -> ui.navigate(HomeView.class));
            }
        });
        register.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout form = new FormLayout(username, email, password, phoneNumber, dateOfBirth);
        VerticalLayout layout = new VerticalLayout(new H3("Register"), form, register);
        layout.setPadding(false);
        return layout;
    }

    // ── Guest tab ──────────────────────────────────────────────────────────────

    private VerticalLayout guestSection() {
        Button startGuestSession = new Button("Continue as guest", event -> {
            AuthResult result = presenter.startGuestSession();
            handle(result);
            refreshSessionStatus();
            MainLayout.refreshCurrentNavigation();
            if (result.success()) {
                getUI().ifPresent(ui -> ui.navigate(HomeView.class));
            }
        });
        startGuestSession.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout layout = new VerticalLayout(
                new H3("Continue as guest"),
                new Paragraph("Browse events and reserve tickets without creating an account. You can register or log in at any time."),
                startGuestSession
        );
        layout.setPadding(false);
        return layout;
    }

    // ── Admin login tab ────────────────────────────────────────────────────────

    private VerticalLayout adminLoginSection() {
        TextField username = new TextField("Admin username");
        PasswordField password = new PasswordField("Admin password");
        markRequired(username, "Admin username is required.");
        markRequired(password, "Admin password is required.");

        Button login = new Button("Log in as admin", event -> {
            AuthResult result = presenter.adminLogin(username.getValue(), password.getValue());
            handle(result);
            refreshSessionStatus();
            MainLayout.refreshCurrentNavigation();
            if (result.success()) {
                password.clear();
                getUI().ifPresent(ui -> ui.navigate(AdminView.class));
            }
        });
        login.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout layout = new VerticalLayout(
                new H3("Admin login"),
                new Paragraph("For platform administrators only. Regular members should use the Log in tab."),
                new FormLayout(username, password),
                login
        );
        layout.setPadding(false);
        return layout;
    }

    // ── Logout section ─────────────────────────────────────────────────────────

    private HorizontalLayout logoutSection() {
        Button logout = new Button("Log out", event -> {
            AuthResult result = presenter.logout();
            handle(result);
            refreshSessionStatus();
            MainLayout.refreshCurrentNavigation();
            if (result.success()) {
                clearAllFields();
                getUI().ifPresent(ui -> ui.navigate(AuthView.class));
            }
        });

        HorizontalLayout layout = new HorizontalLayout(logout);
        layout.setAlignItems(Alignment.BASELINE);
        return layout;
    }

    // ── Visibility control ─────────────────────────────────────────────────────

    private void refreshSessionStatus() {
        presenter.reconcileStoredSession();
        sessionStatus.setText(presenter.currentSessionLabel());
        var session = presenter.currentSessionState();

        // Tabs + their content are shown for no-session and guest states.
        // Once a member is logged in, only the logout button is shown.
        boolean showTabs = !session.loggedInMember();
        tabs.setVisible(showTabs);
        tabContent.setVisible(showTabs);
        logoutActions.setVisible(session.loggedInMember() || session.guest());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void clearAllFields() {
        clearFields(tabContent);
    }

    private void clearFields(com.vaadin.flow.component.Component root) {
        if (root instanceof com.vaadin.flow.component.HasValue<?, ?> field) {
            field.clear();
        }
        root.getChildren().forEach(this::clearFields);
    }

    private void handle(AuthResult result) {
        if (result.success()) {
            UiMessages.success(result.message());
        } else {
            UiMessages.error(result.message());
        }
    }
}
