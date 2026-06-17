package com.ticketing.presentation.vaadin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ticketing.presentation.vaadin.util.SessionContext;
import com.ticketing.presentation.vaadin.views.AdminView;
import com.ticketing.presentation.vaadin.views.AuthView;
import com.ticketing.presentation.vaadin.views.CompanyView;
import com.ticketing.presentation.vaadin.views.EventsView;
import com.ticketing.presentation.vaadin.views.HomeView;
import com.ticketing.presentation.vaadin.views.MemberView;
import com.ticketing.presentation.vaadin.views.NotificationsView;
import com.ticketing.presentation.vaadin.views.OrdersView;
import com.ticketing.presentation.vaadin.views.QueueView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

@SpringComponent
@UIScope
public class MainLayout extends AppLayout implements AfterNavigationObserver, BeforeEnterObserver {

    private final Tabs navigation = new Tabs();
    private final Map<Class<? extends Component>, Tab> tabsByTarget = new HashMap<>();

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
        tabsByTarget.clear();
        for (NavigationItem item : navigationItems(SessionContext.currentUiState())) {
            Tab tab = tab(item);
            tabsByTarget.put(item.target(), tab);
            navigation.add(tab);
        }
        selectCurrentTab();
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
        if (session.noSession()) {
            items.add(new NavigationItem("Auth", AuthView.class));
            return List.copyOf(items);
        }

        // We check the session role instead of session.systemAdmin() to allow an admin 
        // to log in via the regular "Member" tab to buy tickets without being trapped in the Admin UI.
        if ("Admin".equals(session.role())) {
            items.add(new NavigationItem("Admin", AdminView.class));
            items.add(new NavigationItem("Notifications", NotificationsView.class));
            items.add(new NavigationItem("Auth", AuthView.class));
            return List.copyOf(items);
        }

        items.add(new NavigationItem("Home", HomeView.class));
        items.add(new NavigationItem("Auth", AuthView.class));
        items.add(new NavigationItem("Events", EventsView.class));
        items.add(new NavigationItem("Orders", OrdersView.class));
        if (session.loggedInMember()) {
            items.add(new NavigationItem("Profile", MemberView.class));
            items.add(new NavigationItem("Company", CompanyView.class));
        }
        items.add(new NavigationItem("Notifications", NotificationsView.class));
        return List.copyOf(items);
    }

    private Tab tab(NavigationItem item) {
        RouterLink link = new RouterLink(item.label(), item.target());
        return new Tab(link);
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        selectCurrentTab();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        SessionContext.UiState session = SessionContext.currentUiState();
        Class<?> target = event.getNavigationTarget();

        // We check the session role instead of session.systemAdmin() to allow an admin 
        // to log in via the regular "Member" tab to buy tickets without being trapped in the Admin UI.
        if ("Admin".equals(session.role())) {
            if (target == HomeView.class || target == EventsView.class || target == OrdersView.class || target == MemberView.class || target == CompanyView.class || target == QueueView.class) {
                event.forwardTo(AdminView.class);
                event.getUI().access(() -> 
                        com.ticketing.presentation.vaadin.util.UiMessages.error("Admin context active. Switch to a member account for buyer actions."));
            }
        }
    }

    /**
     * Highlights the navigation tab for the view currently shown, so the tab bar
     * reflects the active route. Without this, {@link Tabs} keeps its default
     * (first tab) selected regardless of the route — e.g. the redirect to
     * {@code AuthView} would leave "Home" highlighted while on the auth page.
     */
    private void selectCurrentTab() {
        Component content = getContent();
        navigation.setSelectedTab(content == null ? null : tabsByTarget.get(content.getClass()));
    }

    record NavigationItem(String label, Class<? extends Component> target) {
    }
}
