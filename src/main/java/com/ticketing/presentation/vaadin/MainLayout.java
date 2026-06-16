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
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
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
        // Branded wordmark: an EQ-bar mark (cyan -> magenta) + the product name in Sora.
        Html wordmark = new Html(
                "<span class='app-wordmark'>"
                        + "<svg class='app-brandmark' width='22' height='18' viewBox='0 0 22 18' aria-hidden='true'>"
                        + "<rect x='0' y='6' width='3' height='6' rx='1.5'/>"
                        + "<rect x='5' y='1' width='3' height='16' rx='1.5'/>"
                        + "<rect x='10' y='7' width='3' height='4' rx='1.5'/>"
                        + "<rect x='15' y='3' width='3' height='12' rx='1.5'/>"
                        + "</svg>"
                        + "Ticketing System"
                        + "</span>");

        refreshNavigation();
        navigation.addClassName("app-nav");

        addToNavbar(wordmark, navigation, themeToggle());
    }

    /** Light/dark toggle: flips the {@code theme} attribute on &lt;html&gt; and persists the choice. */
    private Button themeToggle() {
        Button toggle = new Button(new Icon(VaadinIcon.ADJUST));
        toggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        toggle.addClassName("app-theme-toggle");
        toggle.setAriaLabel("Toggle light or dark theme");
        toggle.setTooltipText("Toggle light / dark");
        toggle.addClickListener(e -> toggle.getElement().executeJs(
                "const el=document.documentElement;"
                        + "const dark=(el.getAttribute('theme')||'').includes('dark');"
                        + "if(dark){el.removeAttribute('theme');localStorage.setItem('showpass-theme','light');}"
                        + "else{el.setAttribute('theme','dark');localStorage.setItem('showpass-theme','dark');}"));
        return toggle;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Apply the visitor's saved light/dark preference (dark is the default).
        getElement().executeJs(
                "const t=localStorage.getItem('showpass-theme');const el=document.documentElement;"
                        + "if(t==='light'){el.removeAttribute('theme');}else if(t==='dark'){el.setAttribute('theme','dark');}");
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
            if (target == HomeView.class || target == EventsView.class || target == OrdersView.class || target == MemberView.class || target == CompanyView.class) {
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
