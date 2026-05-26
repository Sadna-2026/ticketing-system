package com.ticketing.presentation.vaadin.views;

import com.ticketing.presentation.vaadin.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "notifications", layout = MainLayout.class)
@PageTitle("Notifications")
public class NotificationsView extends VerticalLayout {

    public NotificationsView() {
        setPadding(true);
        setSpacing(true);

        add(
                new H2("Notifications"),
                new Paragraph("Real-time and delayed notification display will be implemented here."),
                new Paragraph("Future implementation should connect Vaadin UI to the notification infrastructure without leaking UI details into the application/domain layers.")
        );
    }
}
