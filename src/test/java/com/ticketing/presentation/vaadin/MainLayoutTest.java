package com.ticketing.presentation.vaadin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MainLayout")
class MainLayoutTest {

    @Test
    void GivenMainLayout_WhenRendered_ThenAllMajorVaadinViewsAreReachableFromNavbar() {
        List<String> labels = MainLayout.navigationItems().stream()
                .map(MainLayout.NavigationItem::label)
                .toList();

        assertEquals(7, labels.size());
        assertTrue(labels.containsAll(List.of(
                "Home",
                "Auth",
                "Events",
                "Orders",
                "Company",
                "Admin",
                "Notifications"
        )));
    }
}
