package com.ticketing.presentation.vaadin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;

@DisplayName("MainLayout")
@ExtendWith(VaadinSessionExtension.class)
class MainLayoutTest {

    @Test
    void GivenNoSession_WhenRendered_ThenOnlyAuthNavigationIsVisible() {
        List<String> labels = labelsFor(new SessionContext.UiState(false, false, false, false, null, null));

        assertTrue(labels.contains("Auth"));
        assertFalse(labels.contains("Home"));
        assertFalse(labels.contains("Events"));
        assertFalse(labels.contains("Orders"));
        assertFalse(labels.contains("Notifications"));
        assertFalse(labels.contains("Company"));
        assertFalse(labels.contains("Admin"));
    }

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
    @DisplayName("Global event search (Events) is reachable for both guest and member sessions, not just one role")
    void GivenGuestOrMemberSession_WhenRendered_ThenEventsSearchNavigationIsReachableForBoth() {
        List<String> guestLabels = labelsFor(new SessionContext.UiState(true, true, false, false, null, "Guest"));
        List<String> memberLabels = labelsFor(new SessionContext.UiState(true, false, true, false, "alice", "Member"));

        assertTrue(guestLabels.contains("Events"));
        assertTrue(memberLabels.contains("Events"));
    }

    @Test
    void GivenSystemAdminSession_WhenRendered_ThenAdminNavigationIsVisible() {
        List<String> labels = labelsFor(new SessionContext.UiState(true, false, true, true, "root", "Admin"));

        assertTrue(labels.contains("Admin"));
        assertTrue(labels.contains("Notifications"));
        assertTrue(labels.contains("Auth"));
        
        assertFalse(labels.contains("Company"));
        assertFalse(labels.contains("Home"));
        assertFalse(labels.contains("Events"));
        assertFalse(labels.contains("Orders"));
        assertFalse(labels.contains("Profile"));
    }

    private static List<String> labelsFor(SessionContext.UiState state) {
        return MainLayout.navigationItems(state).stream()
                .map(MainLayout.NavigationItem::label)
                .toList();
    }
}
