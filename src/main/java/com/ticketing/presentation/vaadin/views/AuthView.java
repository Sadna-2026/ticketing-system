package com.ticketing.presentation.vaadin.views;

import com.ticketing.presentation.vaadin.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "auth", layout = MainLayout.class)
@PageTitle("Authentication")
public class AuthView extends VerticalLayout {

    public AuthView() {
        setPadding(true);
        setSpacing(true);

        add(
                new H2("Authentication"),
                new Paragraph("Guest entry, login, registration, and logout screens will be implemented here."),
                new Paragraph("Future implementation: AuthView -> AuthPresenter -> application services.")
        );
    }
}
