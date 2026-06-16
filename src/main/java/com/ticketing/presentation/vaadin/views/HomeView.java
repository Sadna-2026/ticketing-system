package com.ticketing.presentation.vaadin.views;

import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
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

        Div inner = new Div(eyebrow, title, sub, browse);
        inner.addClassName("app-hero-inner");

        hero.add(heroWave(), inner);
        return hero;
    }

    /** Decorative animated waveform behind the hero — the brand's signature opener.
     *  CSS-animated (scaleY pulse), gated on prefers-reduced-motion in the theme. */
    private Html heroWave() {
        int n = 48;
        int w = 1200;
        int h = 120;
        double step = (double) w / n;
        double barW = step * 0.5;
        StringBuilder sb = new StringBuilder();
        sb.append("<span class='app-hero-wave' aria-hidden='true'>");
        sb.append("<svg viewBox='0 0 ").append(w).append(' ').append(h)
                .append("' preserveAspectRatio='none' width='100%' height='100%'>");
        sb.append("<defs><linearGradient id='hw' x1='0' x2='1' y1='0' y2='0'>")
                .append("<stop offset='0' stop-color='var(--app-cyan)'/>")
                .append("<stop offset='1' stop-color='var(--app-magenta)'/></linearGradient></defs>");
        for (int i = 0; i < n; i++) {
            double frac = (double) i / n;
            double base = 0.30 + 0.70 * Math.abs(Math.sin(frac * Math.PI * 3));
            double bh = base * h;
            double x = i * step;
            double y = h - bh;
            sb.append(String.format(java.util.Locale.US,
                    "<rect x='%.2f' y='%.2f' width='%.2f' height='%.2f' rx='%.2f' fill='url(#hw)' style='animation-delay:%.2fs'/>",
                    x, y, barW, bh, barW / 2, i * 0.045));
        }
        sb.append("</svg></span>");
        return new Html(sb.toString());
    }

    private Div quickActions() {
        Div grid = new Div();
        grid.addClassName("app-quickgrid");

        // (Browsing events is the hero's primary CTA above, so it isn't repeated here.)
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
