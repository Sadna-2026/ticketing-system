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
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Ticketing System")
@SpringComponent
@UIScope
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

    /**
     * Redirect unauthenticated visitors (no session at all) to the auth page.
     * Guests and logged-in members are allowed through normally.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (SessionContext.currentUiState().noSession()) {
            event.forwardTo(AuthView.class);
        }
    }
}

