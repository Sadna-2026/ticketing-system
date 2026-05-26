package com.ticketing.presentation.vaadin.views;

import com.ticketing.presentation.vaadin.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "events", layout = MainLayout.class)
@PageTitle("Events")
public class EventsView extends VerticalLayout {

    public EventsView() {
        setPadding(true);
        setSpacing(true);

        add(
                new H2("Events"),
                new Paragraph("Event search, event details, and venue map screens will be implemented here."),
                new Paragraph("Future implementation: EventsView -> EventsPresenter -> application services.")
        );
    }
}
