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
        int n = 90;
        int w = 1200;
        int h = 120;
        double step = (double) w / n;
        double barW = step * 0.52;
        StringBuilder sb = new StringBuilder();
        sb.append("<span class='app-hero-wave' aria-hidden='true'>");
        sb.append("<svg viewBox='0 0 ").append(w).append(' ').append(h)
                .append("' preserveAspectRatio='none' width='100%' height='100%'>");
        sb.append("<defs><linearGradient id='hw' x1='0' x2='1' y1='0' y2='0'>")
                .append("<stop offset='0' stop-color='var(--app-cyan)'/>")
                .append("<stop offset='1' stop-color='var(--app-magenta)'/></linearGradient></defs>");
        for (int i = 0; i < n; i++) {
            // Jagged, audio-like silhouette: layered sines including a high-frequency
            // term so neighbouring bars differ (not a few smooth humps).
            double v = Math.sin(i * 0.9) * 0.5
                    + Math.sin(i * 2.3 + 1.7) * 0.3
                    + Math.sin(i * 5.1 + 0.4) * 0.2;
            double base = 0.12 + 0.88 * ((v + 1.0) / 2.0);
            double bh = base * h;
            double x = i * step;
            double y = h - bh;
            // Per-bar pseudo-random timing so bars shimmer independently, like a
            // spectrum analyser, rather than pulsing in unison.
            double r1 = pseudo(i * 12.9898);
            double r2 = pseudo(i * 78.233 + 3.1);
            double dur = 0.9 + r1 * 1.5; // 0.9..2.4s
            double delay = r2 * 1.8; // 0..1.8s
            double lo = 0.28 + r1 * 0.5; // per-bar trough 0.28..0.78
            sb.append(String.format(java.util.Locale.US,
                    "<rect x='%.2f' y='%.2f' width='%.2f' height='%.2f' rx='%.2f' fill='url(#hw)'"
                            + " style='--lo:%.2f;animation-duration:%.2fs;animation-delay:%.2fs'/>",
                    x, y, barW, bh, barW / 2, lo, dur, delay));
        }
        sb.append("</svg></span>");
        return new Html(sb.toString());
    }

    /** Deterministic pseudo-random in [0,1) so the waveform is stable per render. */
    private static double pseudo(double seed) {
        double s = Math.sin(seed) * 43758.5453;
        return s - Math.floor(s);
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
