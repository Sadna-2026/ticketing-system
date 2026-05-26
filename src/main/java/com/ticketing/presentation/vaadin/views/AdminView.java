package com.ticketing.presentation.vaadin.views;

import com.ticketing.presentation.vaadin.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "admin", layout = MainLayout.class)
@PageTitle("Admin")
public class AdminView extends VerticalLayout {

    public AdminView() {
        setPadding(true);
        setSpacing(true);

        add(
                new H2("Admin"),
                new Paragraph("System admin and policy management screens will be implemented here."),
                new Paragraph("Future implementation: AdminView -> AdminPresenter -> application services.")
        );
    }
}
