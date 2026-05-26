package com.ticketing.presentation.vaadin.views;

import com.ticketing.presentation.vaadin.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "company", layout = MainLayout.class)
@PageTitle("Company")
public class CompanyView extends VerticalLayout {

    public CompanyView() {
        setPadding(true);
        setSpacing(true);

        add(
                new H2("Company"),
                new Paragraph("Company owner and manager actions will be implemented here."),
                new Paragraph("Future implementation: CompanyView -> CompanyPresenter -> application services.")
        );
    }
}
