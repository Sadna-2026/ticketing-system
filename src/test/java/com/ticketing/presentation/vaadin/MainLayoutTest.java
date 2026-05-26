package com.ticketing.presentation.vaadin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.presentation.vaadin.util.SessionContext;

@DisplayName("MainLayout")
class MainLayoutTest {

    @Test
    void GivenGuestSession_WhenRendered_ThenOwnerAndAdminNavigationAreHidden() {
        List<String> labels = labelsFor(new SessionContext.UiState(true, true, false, false, null, "Guest"));

        assertTrue(labels.contains("Home"));
        assertTrue(labels.contains("Auth"));
        assertTrue(labels.contains("Events"));
        assertTrue(labels.contains("Orders"));
        assertFalse(labels.contains("Company"));
        assertFalse(labels.contains("Admin"));
    }

    @Test
    void GivenMemberSession_WhenRendered_ThenCompanyNavigationIsVisibleAndAdminNavigationIsHidden() {
        List<String> labels = labelsFor(new SessionContext.UiState(true, false, true, false, "alice", "Member"));

        assertTrue(labels.contains("Company"));
        assertFalse(labels.contains("Admin"));
    }

    @Test
    void GivenSystemAdminSession_WhenRendered_ThenAdminNavigationIsVisible() {
        List<String> labels = labelsFor(new SessionContext.UiState(true, false, true, true, "root", "Member"));

        assertTrue(labels.contains("Company"));
        assertTrue(labels.contains("Admin"));
    }

    private static List<String> labelsFor(SessionContext.UiState state) {
        return MainLayout.navigationItems(state).stream()
                .map(MainLayout.NavigationItem::label)
                .toList();
    }
}
