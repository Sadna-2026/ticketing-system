package com.ticketing.presentation.vaadin.views;

import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Ticketing System")
public class HomeView extends VerticalLayout implements BeforeEnterObserver {

    public HomeView() {
        setPadding(true);
        setSpacing(true);

        add(
                new H2("Ticketing System UI"),
                new Paragraph("Vaadin browser UI foundation is ready."),
                new Paragraph("Use the navigation bar to reach each functional area.")
        );
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (SessionContext.currentUiState().noSession()) {
            event.forwardTo(AuthView.class);
        }
    }
}
