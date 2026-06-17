package com.ticketing.presentation.vaadin.components;

import java.util.List;

import com.ticketing.application.dto.EventPolicyBadgeDTO;
import com.ticketing.application.dto.EventPolicyBadgeDTO.Kind;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Renders purchase restrictions and visible discounts as compact info cards.
 */
public class PolicyBadgesPanel extends VerticalLayout {

    public PolicyBadgesPanel(
            List<EventPolicyBadgeDTO> restrictions,
            List<EventPolicyBadgeDTO> discounts
    ) {
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        if (restrictions != null && !restrictions.isEmpty()) {
            add(section("Purchase restrictions", VaadinIcon.SHIELD.create(), restrictions));
        }
        if (discounts != null && !discounts.isEmpty()) {
            add(section("Available discounts", VaadinIcon.GIFT.create(), discounts));
        }
    }

    private VerticalLayout section(String heading, Icon sectionIcon, List<EventPolicyBadgeDTO> badges) {
        sectionIcon.setSize("18px");
        sectionIcon.getStyle().set("margin-right", "var(--lumo-space-xs)");

        HorizontalLayout title = new HorizontalLayout(sectionIcon, new H4(heading));
        title.setAlignItems(Alignment.CENTER);
        title.setPadding(false);
        title.setSpacing(false);

        VerticalLayout cards = new VerticalLayout();
        cards.setPadding(false);
        cards.setSpacing(true);
        cards.setWidthFull();
        for (EventPolicyBadgeDTO badge : badges) {
            cards.add(card(badge));
        }

        VerticalLayout section = new VerticalLayout(title, cards);
        section.setPadding(false);
        section.setSpacing(true);
        section.setWidthFull();
        return section;
    }

    private Div card(EventPolicyBadgeDTO badge) {
        boolean discount = badge.kind() == Kind.DISCOUNT;
        Div card = new Div();
        card.getStyle()
                .set("display", "flex")
                .set("gap", "var(--lumo-space-m)")
                .set("align-items", "flex-start")
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("border", discount
                        ? "1px solid var(--lumo-success-color-50pct)"
                        : "1px solid var(--lumo-error-color-50pct)")
                .set("background", discount
                        ? "var(--lumo-success-color-10pct)"
                        : "var(--lumo-error-color-10pct)");

        Icon icon = (discount ? VaadinIcon.TAG : VaadinIcon.EXCLAMATION_CIRCLE).create();
        icon.setSize("20px");
        icon.getStyle().set("flex-shrink", "0");
        icon.getStyle().set("margin-top", "2px");
        icon.setColor(discount ? "var(--lumo-success-text-color)" : "var(--lumo-error-text-color)");

        VerticalLayout text = new VerticalLayout();
        text.setPadding(false);
        text.setSpacing(false);
        Span title = new Span(badge.title());
        title.getStyle()
                .set("font-weight", "600")
                .set("font-size", "var(--lumo-font-size-s)");
        Span detail = new Span(badge.detail());
        detail.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        text.add(title, detail);

        card.add(icon, text);
        return card;
    }
}
