package com.ticketing.presentation.vaadin;

import java.util.List;

import com.ticketing.presentation.vaadin.views.AdminView;
import com.ticketing.presentation.vaadin.views.AuthView;
import com.ticketing.presentation.vaadin.views.CompanyView;
import com.ticketing.presentation.vaadin.views.EventsView;
import com.ticketing.presentation.vaadin.views.HomeView;
import com.ticketing.presentation.vaadin.views.NotificationsView;
import com.ticketing.presentation.vaadin.views.OrdersView;
import com.vaadin.flow.component.Component;
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

        Tabs navigation = new Tabs(navigationItems().stream()
                .map(this::tab)
                .toArray(Tab[]::new));
        navigation.getStyle().set("margin-left", "var(--lumo-space-m)");

        addToNavbar(title, navigation);
    }

    static List<NavigationItem> navigationItems() {
        return List.of(
                new NavigationItem("Home", HomeView.class),
                new NavigationItem("Auth", AuthView.class),
                new NavigationItem("Events", EventsView.class),
                new NavigationItem("Orders", OrdersView.class),
                new NavigationItem("Company", CompanyView.class),
                new NavigationItem("Admin", AdminView.class),
                new NavigationItem("Notifications", NotificationsView.class)
        );
    }

    private Tab tab(NavigationItem item) {
        RouterLink link = new RouterLink(item.label(), item.target());
        return new Tab(link);
    }

    record NavigationItem(String label, Class<? extends Component> target) {
    }
}
