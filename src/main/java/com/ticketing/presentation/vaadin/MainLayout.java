package com.ticketing.presentation.vaadin;

import com.ticketing.presentation.vaadin.views.AdminView;
import com.ticketing.presentation.vaadin.views.AuthView;
import com.ticketing.presentation.vaadin.views.CompanyView;
import com.ticketing.presentation.vaadin.views.EventsView;
import com.ticketing.presentation.vaadin.views.HomeView;
import com.ticketing.presentation.vaadin.views.NotificationsView;
import com.ticketing.presentation.vaadin.views.OrdersView;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.RouterLink;

public class MainLayout extends AppLayout {

    public MainLayout() {
        H1 title = new H1("Ticketing System");
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "0 var(--lumo-space-m) 0 0");

        Tabs navigation = new Tabs(
                tab("Home", HomeView.class),
                tab("Auth", AuthView.class),
                tab("Events", EventsView.class),
                tab("Orders", OrdersView.class),
                tab("Company", CompanyView.class),
                tab("Admin", AdminView.class),
                tab("Notifications", NotificationsView.class)
        );
        navigation.getStyle().set("margin-left", "var(--lumo-space-m)");

        addToNavbar(title, navigation);
    }

    private Tab tab(String label, Class<? extends com.vaadin.flow.component.Component> target) {
        RouterLink link = new RouterLink(label, target);
        return new Tab(link);
    }
}
