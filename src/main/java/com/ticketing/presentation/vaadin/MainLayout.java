package com.ticketing.presentation.vaadin;

import java.util.ArrayList;
import java.util.List;

import com.ticketing.presentation.vaadin.util.SessionContext;
import com.ticketing.presentation.vaadin.views.AdminView;
import com.ticketing.presentation.vaadin.views.AuthView;
import com.ticketing.presentation.vaadin.views.CompanyView;
import com.ticketing.presentation.vaadin.views.EventsView;
import com.ticketing.presentation.vaadin.views.HomeView;
import com.ticketing.presentation.vaadin.views.MemberView;
import com.ticketing.presentation.vaadin.views.NotificationsView;
import com.ticketing.presentation.vaadin.views.OrdersView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.RouterLink;

public class MainLayout extends AppLayout {

    private final Tabs navigation = new Tabs();

    public MainLayout() {
        H1 title = new H1("Ticketing System");
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "0 var(--lumo-space-m) 0 0");

        refreshNavigation();
        navigation.getStyle().set("margin-left", "var(--lumo-space-m)");

        addToNavbar(title, navigation);
    }

    public void refreshNavigation() {
        navigation.removeAll();
        for (NavigationItem item : navigationItems(SessionContext.currentUiState())) {
            navigation.add(tab(item));
        }
    }

    public static void refreshCurrentNavigation() {
        UI ui = UI.getCurrent();
        if (ui == null) {
            return;
        }
        ui.getChildren()
                .filter(MainLayout.class::isInstance)
                .map(MainLayout.class::cast)
                .findFirst()
                .ifPresent(MainLayout::refreshNavigation);
    }

    static List<NavigationItem> navigationItems(SessionContext.UiState session) {
        List<NavigationItem> items = new ArrayList<>();
        items.add(new NavigationItem("Home", HomeView.class));
        items.add(new NavigationItem("Auth", AuthView.class));
        items.add(new NavigationItem("Events", EventsView.class));
        items.add(new NavigationItem("Orders", OrdersView.class));
        if (session.loggedInMember()) {
            items.add(new NavigationItem("Profile", MemberView.class));
            items.add(new NavigationItem("Company", CompanyView.class));
        }
        if (session.systemAdmin()) {
            items.add(new NavigationItem("Admin", AdminView.class));
        }
        items.add(new NavigationItem("Notifications", NotificationsView.class));
        return List.copyOf(items);
    }

    private Tab tab(NavigationItem item) {
        RouterLink link = new RouterLink(item.label(), item.target());
        return new Tab(link);
    }

    record NavigationItem(String label, Class<? extends Component> target) {
    }
}
