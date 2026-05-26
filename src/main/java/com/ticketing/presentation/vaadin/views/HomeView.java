package com.ticketing.presentation.vaadin.views;

import com.ticketing.presentation.vaadin.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Ticketing System")
public class HomeView extends VerticalLayout {

    public HomeView() {
        setPadding(true);
        setSpacing(true);

        add(
                new H2("Ticketing System UI"),
                new Paragraph("Vaadin browser UI foundation is ready."),
                new Paragraph("Use the navigation bar to reach each functional area.")
        );
    }
}
