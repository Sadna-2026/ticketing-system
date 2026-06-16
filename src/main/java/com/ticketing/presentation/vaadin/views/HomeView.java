package com.ticketing.presentation.vaadin.views;

import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Ticketing System")
@SpringComponent
@UIScope
public class HomeView extends VerticalLayout implements BeforeEnterObserver {

    public HomeView() {
        setPadding(true);
        setSpacing(false);
        addClassName("app-home");

        add(hero(), quickActions());
    }

    private Div hero() {
        Div hero = new Div();
        hero.addClassName("app-hero");

        Span eyebrow = new Span("Live events · pick your seat");
        eyebrow.addClassName("app-hero-eyebrow");

        H1 title = new H1("Find your seat at the show");
        title.addClassName("app-hero-title");

        Paragraph sub = new Paragraph(
                "Browse published events, choose seats on the map, and manage your orders — all in one place.");
        sub.addClassName("app-hero-sub");

        Button browse = new Button("Browse events", new Icon(VaadinIcon.TICKET));
        browse.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        browse.addClickListener(e -> browse.getUI().ifPresent(ui -> ui.navigate(EventsView.class)));

        hero.add(eyebrow, title, sub, browse);
        return hero;
    }

    private Div quickActions() {
        Div grid = new Div();
        grid.addClassName("app-quickgrid");

        grid.add(actionCard(VaadinIcon.SEARCH, "Browse events",
                "Search shows and pick your seats.", EventsView.class));
        grid.add(actionCard(VaadinIcon.CART, "Your orders",
                "Review your cart, check out, and see purchase history.", OrdersView.class));
        grid.add(actionCard(VaadinIcon.BELL, "Notifications",
                "Role offers and account updates.", NotificationsView.class));

        if (SessionContext.currentUiState().loggedInMember()) {
            grid.add(actionCard(VaadinIcon.USER, "Profile",
                    "Manage your account details.", MemberView.class));
            grid.add(actionCard(VaadinIcon.BUILDING, "Company",
                    "Manage companies, events, inventory, and policies.", CompanyView.class));
        }
        return grid;
    }

    private RouterLink actionCard(VaadinIcon icon, String title, String description,
            Class<? extends Component> target) {
        RouterLink link = new RouterLink("", target);
        link.removeAll();
        link.addClassName("app-action-card");

        Icon glyph = new Icon(icon);
        glyph.addClassName("app-action-icon");
        H3 heading = new H3(title);
        Paragraph desc = new Paragraph(description);

        link.add(glyph, heading, desc);
        return link;
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
