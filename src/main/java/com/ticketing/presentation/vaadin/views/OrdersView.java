package com.ticketing.presentation.vaadin.views;

import com.ticketing.presentation.vaadin.MainLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "orders", layout = MainLayout.class)
@PageTitle("Orders")
public class OrdersView extends VerticalLayout {

    public OrdersView() {
        setPadding(true);
        setSpacing(true);

        add(
                new H2("Orders"),
                new Paragraph("Active order, cart management, checkout, and purchase history screens will be implemented here."),
                new Paragraph("Future implementation: OrdersView -> OrdersPresenter -> application services.")
        );
    }
}
