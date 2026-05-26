package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.SuspensionDTO;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.PurchaseHistoryResult;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.SuspensionListResult;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.Query;

@DisplayName("AdminView")
class AdminViewTest {

    @BeforeEach
    void setUp() {
        UI.setCurrent(new UI());
    }

    @AfterEach
    void tearDown() {
        UI.setCurrent(null);
    }

    @Test
    void GivenAdminView_WhenRendered_ThenSystemAdminGroupsExist() {
        AdminView view = new AdminView(mockPresenter());

        assertTrue(hasText(view, "System admin actions"));
        assertTrue(hasText(view, "Admin-only controls are kept separate from company owner and manager workflows."));
        assertTrue(hasText(view, "Application services still enforce system-admin authorization for every action and their responses are shown here."));
        assertTrue(hasButton(view, "Remove member"));
        assertTrue(hasButton(view, "Load global purchase history"));
        assertTrue(hasButton(view, "Suspend member"));
        assertTrue(hasButton(view, "Cancel suspension"));
        assertTrue(hasButton(view, "Load suspensions"));
        assertNotNull(findTextField(view, "Target member ID"));
        assertNotNull(findTextField(view, "Buyer member ID"));
        assertNotNull(findTextField(view, "Company name"));
        assertNotNull(findTextField(view, "Suspension target member ID"));
        assertNotNull(findTextField(view, "Suspension ID"));
        assertEquals(2, findGrids(view).size());
    }

    @Test
    void GivenAdminView_WhenRendered_ThenCompanyManagementControlsAreHidden() {
        AdminView view = new AdminView(mockPresenter());

        assertFalse(hasButton(view, "Open company"));
        assertFalse(hasButton(view, "Offer role appointment"));
        assertFalse(hasButton(view, "Create company event"));
        assertFalse(hasButton(view, "Load company purchase history"));
        assertFalse(hasButton(view, "Check policy backend support"));
        assertNull(findTextField(view, "Policy company name"));
        assertFalse(hasText(view, "Policy UI placeholders are waiting for backend/application support: #149."));
    }

    @Test
    void GivenRegularMemberSession_WhenRendered_ThenAdminControlsAreHiddenWithExplanation() {
        AdminPresenter presenter = mock(AdminPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Member (alice)");
        when(presenter.currentSessionState()).thenReturn(member());

        AdminView view = new AdminView(presenter);

        assertFalse(hasVisibleButton(view, "Remove member"));
        assertFalse(hasVisibleButton(view, "Load global purchase history"));
        assertFalse(hasVisibleButton(view, "Suspend member"));
        assertFalse(hasVisibleButton(view, "Check policy backend support"));
        assertTrue(hasText(view, "Log in with system admin permissions to use admin actions."));
    }

    @Test
    void GivenTargetMember_WhenRemoveClicked_ThenPresenterIsCalledAndStatusIsDisplayed() {
        AdminPresenter presenter = mockPresenter();
        UUID targetId = UUID.randomUUID();
        when(presenter.removeMember(targetId)).thenReturn(ActionResult.success("Member removed."));
        AdminView view = new AdminView(presenter);
        findTextField(view, "Target member ID").setValue(targetId.toString());

        clickButton(view, "Remove member");

        verify(presenter).removeMember(targetId);
        assertTrue(hasText(view, "Member removed."));
    }

    @Test
    void GivenInvalidTargetMemberId_WhenRemoveClicked_ThenInvalidIdMessageIsDisplayedBeforePresenterCall() {
        AdminPresenter presenter = mockPresenter();
        AdminView view = new AdminView(presenter);
        findTextField(view, "Target member ID").setValue("not-a-uuid");

        clickButton(view, "Remove member");

        assertTrue(hasText(view, "Enter a valid target member ID."));
        verify(presenter).currentSessionLabel();
        verify(presenter).currentSessionState();
        verifyNoMoreInteractions(presenter);
    }

    @Test
    void GivenHistoryRows_WhenLoadGlobalHistoryClicked_ThenGridDisplaysPurchases() {
        AdminPresenter presenter = mockPresenter();
        UUID buyerId = UUID.randomUUID();
        PurchaseRecordDTO purchase = purchase(buyerId);
        when(presenter.loadGlobalPurchaseHistory(buyerId, "Acme"))
                .thenReturn(PurchaseHistoryResult.success("Loaded 1 purchase(s).", List.of(purchase)));
        AdminView view = new AdminView(presenter);
        findTextField(view, "Buyer member ID").setValue(buyerId.toString());
        findTextField(view, "Company name").setValue("Acme");

        clickButton(view, "Load global purchase history");

        verify(presenter).loadGlobalPurchaseHistory(buyerId, "Acme");
        assertTrue(hasText(view, "Loaded 1 purchase(s)."));
        assertEquals(List.of(purchase), findPurchaseHistoryGrid(view).getDataProvider().fetch(new Query<>()).toList());
    }

    @Test
    void GivenApplicationError_WhenLoadGlobalHistoryClicked_ThenMessageIsDisplayedAndGridCleared() {
        AdminPresenter presenter = mockPresenter();
        when(presenter.loadGlobalPurchaseHistory(null, "Acme"))
                .thenReturn(PurchaseHistoryResult.failure("System admin permission required"));
        AdminView view = new AdminView(presenter);
        findTextField(view, "Company name").setValue("Acme");

        clickButton(view, "Load global purchase history");

        assertTrue(hasText(view, "System admin permission required"));
        assertEquals(0, findPurchaseHistoryGrid(view).getDataProvider().fetch(new Query<>()).count());
    }

    @Test
    void GivenSuspensionInputs_WhenActionsClicked_ThenPresenterMethodsAreCalledAndRowsDisplay() {
        AdminPresenter presenter = mockPresenter();
        UUID targetId = UUID.randomUUID();
        UUID suspensionId = UUID.randomUUID();
        SuspensionDTO suspension = suspension(targetId, suspensionId);
        when(presenter.suspendUser(targetId, 7, false, "Spam")).thenReturn(ActionResult.success("Member suspended for 7 day(s)."));
        when(presenter.cancelSuspension(targetId, suspensionId)).thenReturn(ActionResult.success("Suspension cancelled."));
        when(presenter.listSuspensions(true)).thenReturn(SuspensionListResult.success("Loaded 1 suspension(s).", List.of(suspension)));
        AdminView view = new AdminView(presenter);
        findTextField(view, "Suspension target member ID").setValue(targetId.toString());
        findTextArea(view, "Suspension reason").setValue("Spam");
        findTextField(view, "Suspension ID").setValue(suspensionId.toString());
        findIntegerField(view, "Duration days").setValue(7);
        findCheckbox(view, "Active suspensions only").setValue(true);

        clickButton(view, "Suspend member");
        clickButton(view, "Cancel suspension");
        clickButton(view, "Load suspensions");

        verify(presenter).suspendUser(targetId, 7, false, "Spam");
        verify(presenter).cancelSuspension(targetId, suspensionId);
        verify(presenter).listSuspensions(true);
        assertTrue(hasText(view, "Loaded 1 suspension(s)."));
        assertEquals(List.of(suspension), findSuspensionsGrid(view).getDataProvider().fetch(new Query<>()).toList());
    }

    private AdminPresenter mockPresenter() {
        AdminPresenter presenter = mock(AdminPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Member (root)");
        when(presenter.currentSessionState()).thenReturn(admin());
        return presenter;
    }

    private static PurchaseRecordDTO purchase(UUID memberId) {
        return new PurchaseRecordDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Spring Concert",
                "Acme",
                memberId,
                "TXN-1",
                new BigDecimal("80.00"),
                Instant.parse("2026-05-26T12:00:00Z")
        );
    }

    private static SuspensionDTO suspension(UUID memberId, UUID suspensionId) {
        return new SuspensionDTO(
                suspensionId,
                memberId,
                "alice",
                UUID.randomUUID(),
                Instant.parse("2026-05-26T12:00:00Z"),
                Duration.ofDays(7),
                Instant.parse("2026-06-02T12:00:00Z"),
                false,
                true,
                false,
                "Spam"
        );
    }

    private static boolean hasButton(Component root, String text) {
        return components(root).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .anyMatch(button -> text.equals(button.getText()));
    }

    private static boolean hasVisibleButton(Component root, String text) {
        return components(root).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> text.equals(button.getText()))
                .anyMatch(AdminViewTest::isEffectivelyVisible);
    }

    private static void clickButton(Component root, String text) {
        Button button = components(root).stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(candidate -> text.equals(candidate.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Button not found: " + text));
        button.click();
    }

    private static TextField findTextField(Component root, String label) {
        return components(root).stream()
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(field -> label.equals(field.getLabel()))
                .findFirst()
                .orElse(null);
    }

    private static TextArea findTextArea(Component root, String label) {
        return components(root).stream()
                .filter(TextArea.class::isInstance)
                .map(TextArea.class::cast)
                .filter(field -> label.equals(field.getLabel()))
                .findFirst()
                .orElse(null);
    }

    private static IntegerField findIntegerField(Component root, String label) {
        return components(root).stream()
                .filter(IntegerField.class::isInstance)
                .map(IntegerField.class::cast)
                .filter(field -> label.equals(field.getLabel()))
                .findFirst()
                .orElse(null);
    }

    private static Checkbox findCheckbox(Component root, String label) {
        return components(root).stream()
                .filter(Checkbox.class::isInstance)
                .map(Checkbox.class::cast)
                .filter(field -> label.equals(field.getLabel()))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static Grid<PurchaseRecordDTO> findPurchaseHistoryGrid(Component root) {
        return (Grid<PurchaseRecordDTO>) findGridById(root, "admin-global-purchases-grid");
    }

    @SuppressWarnings("unchecked")
    private static Grid<SuspensionDTO> findSuspensionsGrid(Component root) {
        return (Grid<SuspensionDTO>) findGridById(root, "admin-suspensions-grid");
    }

    private static Grid<?> findGridById(Component root, String id) {
        return findGrids(root).stream()
                .filter(grid -> grid.getId().map(id::equals).orElse(false))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Grid not found: " + id));
    }

    private static List<Grid<?>> findGrids(Component root) {
        List<Grid<?>> grids = new ArrayList<>();
        for (Component component : components(root)) {
            if (component instanceof Grid<?> grid) {
                grids.add(grid);
            }
        }
        return grids;
    }

    private static boolean hasText(Component root, String expected) {
        return components(root).stream()
                .filter(HasText.class::isInstance)
                .map(HasText.class::cast)
                .anyMatch(component -> expected.equals(component.getText()));
    }

    private static List<Component> components(Component root) {
        List<Component> result = new ArrayList<>();
        collect(root, result);
        return result;
    }

    private static boolean isEffectivelyVisible(Component component) {
        if (!component.isVisible()) {
            return false;
        }
        return component.getParent()
                .map(AdminViewTest::isEffectivelyVisible)
                .orElse(true);
    }

    private static void collect(Component component, List<Component> result) {
        result.add(component);
        component.getChildren().forEach(child -> collect(child, result));
    }

    private static SessionContext.UiState member() {
        return new SessionContext.UiState(true, false, true, false, "alice", "Member");
    }

    private static SessionContext.UiState admin() {
        return new SessionContext.UiState(true, false, true, true, "root", "Member");
    }
}
