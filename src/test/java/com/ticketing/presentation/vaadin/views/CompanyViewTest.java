package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.application.dto.CompanyPublicDTO;
import com.ticketing.application.dto.CompanySummaryDTO;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.application.dto.OrgNodeDTO;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.SalesReportDTO;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.CompanyInfoResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventMapResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.LifecycleAccessResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.OrgChartResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.PersonnelAccessResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.PurchaseHistoryResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.SalesReportResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.Query;

@DisplayName("CompanyView")
@ExtendWith(VaadinSessionExtension.class)
class CompanyViewTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void GivenCompanyView_WhenRendered_ThenPublicOwnerFounderAndManagerGroupsExist() {
        CompanyView view = new CompanyView(mockPresenter());

        assertTrue(hasText(view, "Public company lookup"));
        assertTrue(hasText(view, "Founder company setup"));
        assertTrue(hasText(view, "Personnel and roles"));
        assertTrue(containsText(view, "Choose a section below"));
        assertTrue(hasText(view, "Application services still enforce authorization for every action and their responses are shown in the status area."));
        assertTrue(hasText(view, "Define and manage purchase rules and discount policies at company or event level."));
        assertTrue(hasButton(view, "Open company"));
        assertTrue(hasButton(view, "Load company info"));
        assertTrue(hasButton(view, "Offer role appointment"));
        assertTrue(hasButton(view, "Accept role offer"));
        assertTrue(hasButton(view, "Reject role offer"));
        assertTrue(hasButton(view, "Revoke personnel"));
        assertTrue(hasButton(view, "Change manager permissions"));
        assertTrue(hasButton(view, "Relinquish ownership"));
        assertTrue(hasButton(view, "Create company event"));
        assertTrue(hasButton(view, "Edit event details"));
        assertTrue(hasButton(view, "Publish event"));
        assertTrue(hasButton(view, "Cancel event"));
        assertTrue(hasButton(view, "Load event map"));
        assertTrue(hasButton(view, "Add seat"));
        assertTrue(hasButton(view, "Remove seat"));
        assertTrue(hasButton(view, "Increase GA capacity"));
        assertTrue(hasButton(view, "Decrease GA capacity"));
        assertTrue(hasButton(view, "Set zone price"));
        assertTrue(hasButton(view, "Suspend company"));
        assertTrue(hasButton(view, "Reopen company"));
        assertFalse(hasButton(view, "Close company"));
        assertTrue(hasButton(view, "Load company purchase history"));
        assertTrue(hasButton(view, "Load sales report"));
        assertNotNull(findTextField(view, "New company name"));
        assertNotNull(findTargetMemberCombo(view));
        assertNotNull(findEventCombo(view, "Event to manage"));
        assertNotNull(findCompanyCombo(view, "Event company name"));
        assertEquals(2, findGrids(view).size());
    }

    @Test
    void GivenGuestSession_WhenRendered_ThenPublicCompanyInfoAndMapRemainVisibleButMemberActionsAreHidden() {
        CompanyPresenter presenter = mock(CompanyPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Guest");
        when(presenter.currentSessionState()).thenReturn(guest());

        CompanyView view = new CompanyView(presenter);

        assertTrue(hasVisibleButton(view, "Load company info"));
        assertTrue(hasVisibleButton(view, "Load event map"));
        assertFalse(hasVisibleButton(view, "Open company"));
        assertFalse(hasVisibleButton(view, "Offer role appointment"));
        assertFalse(hasVisibleButton(view, "Relinquish ownership"));
        assertFalse(hasVisibleButton(view, "Create company event"));
        assertFalse(hasVisibleButton(view, "Add seat"));
        assertFalse(hasVisibleButton(view, "Suspend company"));
        assertFalse(hasVisibleButton(view, "Load company purchase history"));
        assertTrue(hasText(view, "Log in as a member to use company owner and manager actions."));
    }

    @Test
    void GivenCompanyView_WhenRendered_ThenAdminOnlyControlsAreHidden() {
        CompanyView view = new CompanyView(mockPresenter());

        assertFalse(hasButton(view, "Remove member"));
        assertFalse(hasButton(view, "Load global purchase history"));
        assertFalse(hasButton(view, "Suspend member"));
        assertFalse(hasButton(view, "Cancel suspension"));
        assertFalse(hasButton(view, "Load suspensions"));
        assertFalse(hasButton(view, "Check policy backend support"));
    }

    @Test
    void GivenCompanyName_WhenLoadingCompanyInfo_ThenCompanyDetailsAndEventsAreDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        EventSummaryDTO event = eventSummary();
        when(presenter.loadCompanyInfo("Acme"))
                .thenReturn(CompanyInfoResult.success("Company information loaded.",
                        new CompanyPublicDTO("Acme", "desc", List.of(event))));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Company info name").setValue(company("Acme"));

        clickButton(view, "Load company info");

        assertTrue(hasText(view, "Acme | desc"));
        Grid<EventSummaryDTO> grid = findEventGrid(view);
        assertEquals(List.of(event), grid.getDataProvider().fetch(new Query<>()).toList());
        verify(presenter).loadCompanyInfo("Acme");
    }

    @Test
    void GivenPersonnelInputs_WhenRoleActionsClicked_ThenPresenterMethodsAreCalled() {
        CompanyPresenter presenter = mockPresenter();
        UUID targetId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        when(presenter.offerRoleAppointment(eq("Acme"), eq(targetId), eq(StaffAppointment.StaffRole.MANAGER), any()))
                .thenReturn(ActionResult.success("Role appointment offer sent."));
        when(presenter.respondToRoleOffer(offerId, true)).thenReturn(ActionResult.success("Role offer accepted."));
        when(presenter.respondToRoleOffer(offerId, false)).thenReturn(ActionResult.success("Role offer rejected."));
        when(presenter.revokePersonnel("Acme", targetId)).thenReturn(ActionResult.success("Personnel revoked."));
        when(presenter.changeManagerPermissions(eq("Acme"), eq(targetId), any()))
                .thenReturn(ActionResult.success("Manager permissions updated."));
        when(presenter.loadOrganizationChart("Acme"))
                .thenReturn(OrgChartResult.success("Organization chart loaded.", List.of(personnel("manager", targetId))));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));
        selectTargetMember(view, "manager", targetId, StaffAppointment.StaffRole.MANAGER);
        findTextField(view, "Role offer ID").setValue(offerId.toString());
        findCheckboxGroup(view).setValue(Set.of(ManagerPermission.VIEW_REPORTS));

        clickButton(view, "Offer role appointment");
        clickButton(view, "Accept role offer");
        clickButton(view, "Reject role offer");
        clickButton(view, "Change manager permissions");
        clickButton(view, "Revoke personnel");

        verify(presenter).offerRoleAppointment("Acme", targetId, StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.VIEW_REPORTS));
        verify(presenter).respondToRoleOffer(offerId, true);
        verify(presenter).respondToRoleOffer(offerId, false);
        verify(presenter).revokePersonnel("Acme", targetId);
        verify(presenter).changeManagerPermissions("Acme", targetId, Set.of(ManagerPermission.VIEW_REPORTS));
        assertTrue(hasText(view, "Personnel revoked."));
    }

    @Test
    void GivenOwnerForSelectedCompany_WhenPersonnelCompanySelected_ThenChangeManagerPermissionsIsReachable() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.loadPersonnelAccess("Acme"))
                .thenReturn(PersonnelAccessResult.allowed("Owner personnel controls available for Acme."));

        CompanyView view = new CompanyView(presenter);
        selectTab(view, "Personnel");
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));

        assertTrue(hasVisibleButton(view, "Change manager permissions"));
        assertTrue(hasVisibleButton(view, "Revoke personnel"));
        assertFalse(hasText(view, "Only a company owner can manage personnel for Acme."));
    }

    @Test
    void GivenNonOwnerForSelectedCompany_WhenPersonnelCompanySelected_ThenOwnerOnlyPersonnelActionsAreHiddenWithReason() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.loadPersonnelAccess("Acme"))
                .thenReturn(PersonnelAccessResult.denied("Only a company owner can manage personnel for Acme."));

        CompanyView view = new CompanyView(presenter);
        selectTab(view, "Personnel");
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));

        assertFalse(hasVisibleButton(view, "Change manager permissions"));
        assertFalse(hasVisibleButton(view, "Revoke personnel"));
        assertFalse(hasVisibleButton(view, "Load organization chart"));
        assertTrue(hasText(view, "Only a company owner can manage personnel for Acme."));
    }

    @Test
    void GivenNonOwnerForSelectedCompany_WhenPersonnelCompanySelected_ThenPersonnelTargetsAreDisabledWithOwnerOnlyReason() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.loadPersonnelAccess("Acme"))
                .thenReturn(PersonnelAccessResult.denied(
                        "Access denied. Only company owners can view the organization chart."));

        CompanyView view = new CompanyView(presenter);
        selectTab(view, "Personnel");
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));

        ComboBox<Object> target = findTargetMemberCombo(view);
        assertFalse(target.isEnabled());
        assertFalse(target.isInvalid());
        assertEquals("Owner-only personnel list unavailable", target.getPlaceholder());
        assertTrue(targetMemberLabels(view).isEmpty());
        assertFalse(hasVisibleButton(view, "Load organization chart"));
        assertTrue(hasText(view, "Access denied. Only company owners can view the organization chart."));
        verify(presenter, never()).loadOrganizationChart("Acme");
    }

    @Test
    void GivenOwnerAndOrganizationChart_WhenLoadOrganizationChartClicked_ThenRoleTreeShowsPermissionsAndRevokedInRed() {
        CompanyPresenter presenter = mockPresenter();
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID revokedManagerId = UUID.randomUUID();
        OrgNodeDTO revokedManager = new OrgNodeDTO(revokedManagerId, "revoked-manager",
                StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.HANDLE_INQUIRIES, ManagerPermission.VIEW_REPORTS),
                true,
                List.of());
        OrgNodeDTO manager = new OrgNodeDTO(managerId, "manager", StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.INVENTORY_MGMT, ManagerPermission.VIEW_REPORTS),
                false,
                List.of(revokedManager));
        OrgNodeDTO owner = new OrgNodeDTO(ownerId, "owner", StaffAppointment.StaffRole.OWNER,
                Set.of(),
                false,
                List.of(manager));
        when(presenter.loadPersonnelAccess("Acme"))
                .thenReturn(PersonnelAccessResult.allowed("Owner personnel controls available for Acme."));
        when(presenter.loadOrganizationChart("Acme"))
                .thenReturn(OrgChartResult.success("Organization chart loaded.", List.of(owner)));

        CompanyView view = new CompanyView(presenter);
        selectTab(view, "Personnel");
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));

        assertTrue(hasVisibleButton(view, "Load organization chart"));
        clickButton(view, "Load organization chart");

        assertTrue(hasText(view, "Organization chart loaded."));
        assertTrue(hasText(view, "owner"));
        assertTrue(hasText(view, "No manager permissions"));
        assertTrue(hasText(view, "manager"));
        assertTrue(hasText(view, "INVENTORY_MGMT"));
        assertTrue(hasText(view, "VIEW_REPORTS"));
        assertTrue(hasText(view, "revoked-manager"));
        assertTrue(hasText(view, "HANDLE_INQUIRIES"));
        assertTrue(hasText(view, "Revoked"));
        Span revokedSummary = findSpan(view, "revoked-manager");
        assertEquals("var(--lumo-error-text-color)", revokedSummary.getStyle().get("color"));
    }

    @Test
    void GivenPersonnelCompanySelected_WhenOrganizationChartLoads_ThenTargetMemberDropdownShowsUsernameIdAndRole() {
        CompanyPresenter presenter = mockPresenter();
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(presenter.loadOrganizationChart("Acme"))
                .thenReturn(OrgChartResult.success("Organization chart loaded.",
                        List.of(new OrgNodeDTO(ownerId, "owner", StaffAppointment.StaffRole.OWNER,
                                Set.of(), false, List.of(personnel("manager", managerId))))));

        CompanyView view = new CompanyView(presenter);
        selectTab(view, "Personnel");
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));

        List<String> targets = targetMemberLabels(view);
        assertTrue(targets.contains(personnelLabel("owner", ownerId, StaffAppointment.StaffRole.OWNER)));
        assertTrue(targets.contains(personnelLabel("manager", managerId, StaffAppointment.StaffRole.MANAGER)));
    }

    @Test
    void GivenPersonnelCompanySelected_WhenOrganizationChartContainsRevokedManager_ThenTargetDropdownSkipsRevokedPersonnel() {
        CompanyPresenter presenter = mockPresenter();
        UUID activeManagerId = UUID.randomUUID();
        UUID revokedManagerId = UUID.randomUUID();
        when(presenter.loadOrganizationChart("Acme"))
                .thenReturn(OrgChartResult.success("Organization chart loaded.",
                        List.of(personnel("activeManager", activeManagerId),
                                personnel("revokedManager", revokedManagerId, true))));

        CompanyView view = new CompanyView(presenter);
        selectTab(view, "Personnel");
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));

        List<String> targets = targetMemberLabels(view);
        assertTrue(targets.contains(personnelLabel("activeManager", activeManagerId, StaffAppointment.StaffRole.MANAGER)));
        assertFalse(targets.contains(personnelLabel("revokedManager", revokedManagerId, StaffAppointment.StaffRole.MANAGER)));
    }

    @Test
    void GivenOwnerAndServiceRejectsPermissionChange_WhenChangeManagerPermissionsClicked_ThenSpecificReasonIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        UUID targetId = UUID.randomUUID();
        when(presenter.loadPersonnelAccess("Acme"))
                .thenReturn(PersonnelAccessResult.allowed("Owner personnel controls available for Acme."));
        when(presenter.changeManagerPermissions(eq("Acme"), eq(targetId), any()))
                .thenReturn(ActionResult.failure("Only the founder or the direct appointer can modify manager permissions."));
        when(presenter.loadOrganizationChart("Acme"))
                .thenReturn(OrgChartResult.success("Organization chart loaded.", List.of(personnel("manager", targetId))));

        CompanyView view = new CompanyView(presenter);
        selectTab(view, "Personnel");
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));
        selectTargetMember(view, "manager", targetId, StaffAppointment.StaffRole.MANAGER);
        findCheckboxGroup(view).setValue(Set.of(ManagerPermission.EVENT_LIFECYCLE));

        clickButton(view, "Change manager permissions");

        verify(presenter).changeManagerPermissions("Acme", targetId, Set.of(ManagerPermission.EVENT_LIFECYCLE));
        assertTrue(hasText(view, "Only the founder or the direct appointer can modify manager permissions."));
    }

    @Test
    void GivenOwnerAndManagerTarget_WhenRevokePersonnelClicked_ThenManagerRemovalSucceedsThroughUi() {
        CompanyPresenter presenter = mockPresenter();
        UUID targetId = UUID.randomUUID();
        when(presenter.loadPersonnelAccess("Acme"))
                .thenReturn(PersonnelAccessResult.allowed("Owner personnel controls available for Acme."));
        when(presenter.loadOrganizationChart("Acme"))
                .thenReturn(OrgChartResult.success("Organization chart loaded.", List.of(personnel("manager", targetId))));
        when(presenter.revokePersonnel("Acme", targetId))
                .thenReturn(ActionResult.success("Personnel revoked."));

        CompanyView view = new CompanyView(presenter);
        selectTab(view, "Personnel");
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));
        selectTargetMember(view, "manager", targetId, StaffAppointment.StaffRole.MANAGER);

        clickButton(view, "Revoke personnel");

        verify(presenter).revokePersonnel("Acme", targetId);
        assertTrue(hasText(view, "Personnel revoked."));
    }

    @Test
    void GivenOwnerAndServiceRejectsManagerRemoval_WhenRevokePersonnelClicked_ThenSpecificReasonIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        UUID targetId = UUID.randomUUID();
        when(presenter.loadPersonnelAccess("Acme"))
                .thenReturn(PersonnelAccessResult.allowed("Owner personnel controls available for Acme."));
        when(presenter.loadOrganizationChart("Acme"))
                .thenReturn(OrgChartResult.success("Organization chart loaded.", List.of(personnel("manager", targetId))));
        when(presenter.revokePersonnel("Acme", targetId))
                .thenReturn(ActionResult.failure(
                        "Revoker does not have permission to revoke this member. Only the appointer can revoke their appointees."));

        CompanyView view = new CompanyView(presenter);
        selectTab(view, "Personnel");
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));
        selectTargetMember(view, "manager", targetId, StaffAppointment.StaffRole.MANAGER);

        clickButton(view, "Revoke personnel");

        verify(presenter).revokePersonnel("Acme", targetId);
        assertTrue(hasText(view,
                "Revoker does not have permission to revoke this member. Only the appointer can revoke their appointees."));
    }

    @Test
    void GivenEventAndInventoryInputs_WhenActionsClicked_ThenPresenterMethodsAreCalledAndEventIdIsReused() {
        CompanyPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        EventSummaryDTO created = event("Show", eventId);
        when(presenter.createEvent(eq("Acme"), eq("Show"), eq("desc"), eq(EventCategory.CONCERT),
                any(), any(), any(), eq(15), eq("Floor"), eq(new BigDecimal("50.00")), eq(100), eq("Main Hall")))
                .thenReturn(EventActionResult.created("Event created.", eventId));
        when(presenter.listCompanyEvents("Acme")).thenReturn(List.of(created));
        when(presenter.publishEvent(eventId)).thenReturn(ActionResult.success("Event published."));
        when(presenter.cancelEvent(eventId)).thenReturn(ActionResult.success("Event cancelled."));
        when(presenter.loadEventMap(eventId)).thenReturn(EventMapResult.success("Event map loaded.", eventMap(eventId, zoneId)));
        when(presenter.addSeat(eventId, zoneId, "A", "1")).thenReturn(ActionResult.success("Seat added."));
        when(presenter.removeSeat(eventId, zoneId, seatId)).thenReturn(ActionResult.success("Seat removed."));
        when(presenter.increaseGACapacity(eventId, zoneId, 5)).thenReturn(ActionResult.success("GA capacity increased."));
        when(presenter.decreaseGACapacity(eventId, zoneId, 5)).thenReturn(ActionResult.success("GA capacity decreased."));
        when(presenter.setZonePrice(eventId, zoneId, new BigDecimal("75.00"))).thenReturn(ActionResult.success("Zone price updated."));
        CompanyView view = new CompanyView(presenter);
        fillCreateEventForm(view);

        clickButton(view, "Create company event");
        // Creating an event auto-selects it in both the management and inventory pickers.
        assertEquals(eventId, findEventCombo(view, "Event to manage").getValue().id());
        assertEquals(eventId, findEventCombo(view, "Inventory event").getValue().id());

        // The public lookup picker is independent of the management pickers.
        findEventCombo(view, "Published event").setValue(created);
        findTextField(view, "Inventory zone ID").setValue(zoneId.toString());
        findTextField(view, "Seat row").setValue("A");
        findTextField(view, "Seat number").setValue("1");
        findTextField(view, "Seat ID").setValue(seatId.toString());
        findIntegerField(view, "Capacity delta").setValue(5);
        findBigDecimalField(view, "Zone price update").setValue(new BigDecimal("75.00"));

        clickButton(view, "Publish event");
        clickButton(view, "Cancel event");
        clickButton(view, "Load event map");
        clickButton(view, "Add seat");
        clickButton(view, "Remove seat");
        clickButton(view, "Increase GA capacity");
        clickButton(view, "Decrease GA capacity");
        clickButton(view, "Set zone price");

        verify(presenter).publishEvent(eventId);
        verify(presenter).cancelEvent(eventId);
        verify(presenter).loadEventMap(eventId);
        verify(presenter).addSeat(eventId, zoneId, "A", "1");
        verify(presenter).removeSeat(eventId, zoneId, seatId);
        verify(presenter).increaseGACapacity(eventId, zoneId, 5);
        verify(presenter).decreaseGACapacity(eventId, zoneId, 5);
        verify(presenter).setZonePrice(eventId, zoneId, new BigDecimal("75.00"));
        assertTrue(hasText(view, "Zone price updated."));
    }

    @Test
    void GivenLifecycleAndReportingInputs_WhenActionsClicked_ThenResultsAreDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        UUID memberId = UUID.randomUUID();
        PurchaseRecordDTO purchase = purchase(memberId);
        SalesReportDTO report = new SalesReportDTO("Acme", memberId, List.of(purchase), new BigDecimal("80.00"), 1);
        when(presenter.suspendCompany("Acme")).thenReturn(ActionResult.success("Company suspended."));
        when(presenter.reopenCompany("Acme")).thenReturn(ActionResult.success("Company reopened."));
        when(presenter.loadPurchaseHistory("Acme")).thenReturn(PurchaseHistoryResult.success("Loaded 1 purchase(s).", List.of(purchase)));
        when(presenter.loadSalesReport("Acme")).thenReturn(SalesReportResult.success("Sales report loaded.", report));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Lifecycle company name").setValue(company("Acme"));
        findCompanyCombo(view, "Reporting company name").setValue(company("Acme"));

        clickButton(view, "Suspend company");
        clickButton(view, "Reopen company");
        clickButton(view, "Load company purchase history");
        clickButton(view, "Load sales report");

        assertTrue(hasText(view, "Company reopened."));
        assertTrue(hasText(view, "Total purchases: 1"));
        assertTrue(hasText(view, "Total revenue: 80.00"));
        assertEquals(List.of(purchase), findPurchasesGrid(view).getDataProvider().fetch(new Query<>()).toList());
    }

    @Test
    void GivenFounderForSelectedCompany_WhenLifecycleCompanySelected_ThenLifecycleActionsAreReachable() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.loadLifecycleAccess("Acme"))
                .thenReturn(LifecycleAccessResult.allowed("Founder lifecycle controls available for Acme."));

        CompanyView view = new CompanyView(presenter);
        selectTab(view, "Lifecycle");
        findCompanyCombo(view, "Lifecycle company name").setValue(company("Acme"));

        assertTrue(hasVisibleButton(view, "Suspend company"));
        assertTrue(hasVisibleButton(view, "Reopen company"));
        assertFalse(hasVisibleButton(view, "Close company"));
        assertFalse(hasText(view, "Only the founder can perform this lifecycle action"));
    }

    @Test
    void GivenInactiveFounderCompanies_WhenRendered_ThenLifecyclePickerIncludesThem() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.searchLookupCompanies(""))
                .thenReturn(List.of(company("Active Public"), company("Suspended Co")));
        when(presenter.searchCompanies("")).thenReturn(List.of(company("Active Public")));
        when(presenter.searchLifecycleCompanies(""))
                .thenReturn(List.of(company("Suspended Co")));

        CompanyView view = new CompanyView(presenter);

        assertEquals(List.of("Suspended Co"), companyNames(findCompanyCombo(view, "Lifecycle company name")));
        assertEquals(List.of("Active Public", "Suspended Co"), companyNames(findCompanyCombo(view, "Company info name")));
        assertEquals(List.of("Active Public"), companyNames(findCompanyCombo(view, "Personnel company name")));
    }

    @Test
    void GivenCompanySuspended_WhenLifecycleActionSucceeds_ThenSuspendedCompanyRemainsSelectableForReopen() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.searchLifecycleCompanies(""))
                .thenReturn(List.of(company("Acme")))
                .thenReturn(List.of(company("Acme")));
        when(presenter.suspendCompany("Acme")).thenReturn(ActionResult.success("Company suspended."));
        when(presenter.loadLifecycleAccess("Acme"))
                .thenReturn(LifecycleAccessResult.allowed("Founder lifecycle controls available for Acme."));

        CompanyView view = new CompanyView(presenter);
        selectTab(view, "Lifecycle");
        ComboBox<CompanySummaryDTO> lifecyclePicker = findCompanyCombo(view, "Lifecycle company name");
        lifecyclePicker.setValue(company("Acme"));

        clickButton(view, "Suspend company");

        assertEquals(List.of("Acme"), companyNames(lifecyclePicker));
        assertEquals("Acme", lifecyclePicker.getValue().name());
        assertTrue(hasVisibleButton(view, "Reopen company"));
        verify(presenter).suspendCompany("Acme");
        verify(presenter, atLeast(2)).searchLifecycleCompanies("");
    }

    @Test
    void GivenLifecycleActionSucceeds_WhenCompanyStatusChanges_ThenLookupAndActivePickersRefresh() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.searchLookupCompanies(""))
                .thenReturn(List.of(company("Acme")))
                .thenReturn(List.of(company("Acme")));
        when(presenter.searchCompanies(""))
                .thenReturn(List.of(company("Acme")))
                .thenReturn(List.of());
        when(presenter.searchLifecycleCompanies(""))
                .thenReturn(List.of(company("Acme")))
                .thenReturn(List.of(company("Acme")));
        when(presenter.suspendCompany("Acme")).thenReturn(ActionResult.success("Company suspended."));
        when(presenter.loadLifecycleAccess(any()))
                .thenReturn(LifecycleAccessResult.allowed("Founder lifecycle controls available."));

        CompanyView view = new CompanyView(presenter);
        ComboBox<CompanySummaryDTO> lifecyclePicker = findCompanyCombo(view, "Lifecycle company name");
        lifecyclePicker.setValue(company("Acme"));

        clickButton(view, "Suspend company");

        assertEquals(List.of("Acme"), companyNames(findCompanyCombo(view, "Company info name")));
        assertEquals(List.of(), companyNames(findCompanyCombo(view, "Personnel company name")));
        assertEquals("Acme", lifecyclePicker.getValue().name());
        verify(presenter, atLeast(2)).searchLookupCompanies("");
        verify(presenter, atLeast(2)).searchCompanies("");
        assertTrue(hasText(view, "Company suspended."));
    }

    @Test
    void GivenSuspendedCompanySelected_WhenReopenClicked_ThenSuccessMessageIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.searchLifecycleCompanies("")).thenReturn(List.of(company("Suspended Co")));
        when(presenter.reopenCompany("Suspended Co")).thenReturn(ActionResult.success("Company reopened."));
        when(presenter.loadLifecycleAccess("Suspended Co"))
                .thenReturn(LifecycleAccessResult.allowed("Founder lifecycle controls available for Suspended Co."));

        CompanyView view = new CompanyView(presenter);
        selectTab(view, "Lifecycle");
        findCompanyCombo(view, "Lifecycle company name").setValue(company("Suspended Co"));

        clickButton(view, "Reopen company");

        verify(presenter).reopenCompany("Suspended Co");
        assertTrue(hasText(view, "Company reopened."));
    }

    @Test
    void GivenNonFounderForSelectedCompany_WhenLifecycleCompanySelected_ThenLifecycleActionsAreHiddenWithReason() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.loadLifecycleAccess("Acme"))
                .thenReturn(LifecycleAccessResult.denied("Only the founder can perform this lifecycle action"));

        CompanyView view = new CompanyView(presenter);
        selectTab(view, "Lifecycle");
        findCompanyCombo(view, "Lifecycle company name").setValue(company("Acme"));

        assertFalse(hasVisibleButton(view, "Suspend company"));
        assertFalse(hasVisibleButton(view, "Reopen company"));
        assertFalse(hasVisibleButton(view, "Close company"));
        assertTrue(hasText(view, "Only the founder can perform this lifecycle action"));
    }

    @Test
    void GivenCompanyPurchases_WhenRendered_ThenGridShowsEventAndDateNotRawPurchaseId() {
        CompanyPresenter presenter = mockPresenter();
        UUID memberId = UUID.randomUUID();
        PurchaseRecordDTO purchase = purchase(memberId);
        when(presenter.loadPurchaseHistory("Acme")).thenReturn(PurchaseHistoryResult.success("Loaded 1 purchase(s).", List.of(purchase)));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Reporting company name").setValue(company("Acme"));

        clickButton(view, "Load company purchase history");

        Grid<PurchaseRecordDTO> grid = findPurchasesGrid(view);
        List<String> headers = columnHeaders(grid);
        assertFalse(headers.contains("Purchase ID"), headers.toString());
        assertTrue(headers.contains("Event"), headers.toString());
        assertTrue(headers.contains("Purchased at"), headers.toString());
        // The purchase id is still carried by the bound row even though it is no longer a column.
        List<PurchaseRecordDTO> rows = grid.getDataProvider().fetch(new Query<>()).toList();
        assertEquals(purchase.purchaseId(), rows.get(0).purchaseId());
    }

    @Test
    void GivenUnauthorizedApplicationResponse_WhenManagerActionClicked_ThenMessageIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        UUID eventId = UUID.randomUUID();
        when(presenter.publishEvent(eventId)).thenReturn(ActionResult.failure("Insufficient permissions to publish events"));
        when(presenter.listCompanyEvents("Acme")).thenReturn(List.of(event("Show", eventId)));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Event company name").setValue(company("Acme"));
        findEventCombo(view, "Event to manage").setValue(event("Show", eventId));

        clickButton(view, "Publish event");

        verify(presenter).publishEvent(eventId);
        assertTrue(hasText(view, "Insufficient permissions to publish events"));
    }

    @Test
    void GivenCompanySelection_WhenEventPickerCascades_ThenPlaceholderReflectsState() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.listCompanyEvents("Acme")).thenReturn(List.of(event("Show", UUID.randomUUID())));
        when(presenter.listCompanyEvents("Empty Co")).thenReturn(List.of());
        CompanyView view = new CompanyView(presenter);
        ComboBox<EventSummaryDTO> eventPicker = findEventCombo(view, "Event to manage");
        assertEquals("Select a company first", eventPicker.getPlaceholder());

        findCompanyCombo(view, "Event company name").setValue(company("Acme"));
        assertEquals("Select an event", eventPicker.getPlaceholder());

        findCompanyCombo(view, "Event company name").setValue(company("Empty Co"));
        assertEquals("No events for this company", eventPicker.getPlaceholder());
    }

    @Test
    void GivenCompanyView_WhenRendered_ThenMandatoryFieldsShowRequiredIndicatorAndOptionalFieldsDoNot() {
        CompanyView view = new CompanyView(mockPresenter());

        assertTrue(findTextField(view, "New company name").isRequiredIndicatorVisible());
        assertTrue(findCompanyCombo(view, "Personnel company name").isRequiredIndicatorVisible());
        assertTrue(findTargetMemberCombo(view).isRequiredIndicatorVisible());
        assertTrue(findComboByLabel(view, "Role").isRequiredIndicatorVisible());
        assertTrue(findCompanyCombo(view, "Event company name").isRequiredIndicatorVisible());
        assertTrue(findTextField(view, "Event name").isRequiredIndicatorVisible());
        assertTrue(findComboByLabel(view, "Event category").isRequiredIndicatorVisible());
        assertTrue(findDateTimePicker(view, "Start time").isRequiredIndicatorVisible());
        assertTrue(findDateTimePicker(view, "End time").isRequiredIndicatorVisible());
        assertTrue(findIntegerField(view, "Lock minutes").isRequiredIndicatorVisible());
        assertTrue(findTextField(view, "Zone name").isRequiredIndicatorVisible());
        assertTrue(findBigDecimalField(view, "Zone price").isRequiredIndicatorVisible());
        assertTrue(findIntegerField(view, "GA capacity").isRequiredIndicatorVisible());
        assertTrue(findTextField(view, "Venue section").isRequiredIndicatorVisible());

        assertFalse(findTextArea(view, "New company description").isRequiredIndicatorVisible());
        assertFalse(findTextArea(view, "Event description").isRequiredIndicatorVisible());
        assertFalse(findDateTimePicker(view, "Doors open time").isRequiredIndicatorVisible());
        assertFalse(findTextField(view, "Role offer ID").isRequiredIndicatorVisible());
    }

    @Test
    void GivenRequiredField_WhenValueIsClearedToBlank_ThenItShowsInlineError() {
        CompanyView view = new CompanyView(mockPresenter());
        TextField eventName = findTextField(view, "Event name");

        eventName.setValue("Summer Concert");
        assertFalse(eventName.isInvalid());

        eventName.setValue("");
        assertTrue(eventName.isInvalid());
        assertEquals("Event name is required.", eventName.getErrorMessage());
    }

    @Test
    void GivenOwnerAndCompany_WhenLoadSalesReportClicked_ThenReportTotalsAreDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        UUID memberId = UUID.randomUUID();
        PurchaseRecordDTO purchase = purchase(memberId);
        SalesReportDTO report = new SalesReportDTO("Acme", memberId, List.of(purchase), new BigDecimal("80.00"), 1);
        when(presenter.loadSalesReport("Acme")).thenReturn(SalesReportResult.success("Sales report loaded.", report));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Reporting company name").setValue(company("Acme"));

        clickButton(view, "Load sales report");

        verify(presenter).loadSalesReport("Acme");
        assertTrue(hasText(view, "Sales report loaded."));
        assertTrue(hasText(view, "Total purchases: 1"));
        assertTrue(hasText(view, "Total revenue: 80.00"));
    }

    @Test
    void GivenInsufficientPermissions_WhenLoadSalesReportClicked_ThenFailureReasonIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.loadSalesReport("Acme")).thenReturn(SalesReportResult.failure(
                "Viewing sales report requires Owner role or (Manager + VIEW_REPORTS permission)"));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Reporting company name").setValue(company("Acme"));

        clickButton(view, "Load sales report");

        verify(presenter).loadSalesReport("Acme");
        assertTrue(hasText(view, "Viewing sales report requires Owner role or (Manager + VIEW_REPORTS permission)"));
    }

    @Test
    void GivenManagerRoleWithPermissions_WhenOfferRoleAppointmentClicked_ThenSuccessMessageIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        UUID targetId = UUID.randomUUID();
        when(presenter.offerRoleAppointment(eq("Acme"), eq(targetId), eq(StaffAppointment.StaffRole.MANAGER), any()))
                .thenReturn(ActionResult.success("Role appointment offer sent."));
        when(presenter.loadOrganizationChart("Acme"))
                .thenReturn(OrgChartResult.success("Organization chart loaded.", List.of(personnel("manager", targetId))));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));
        selectTargetMember(view, "manager", targetId, StaffAppointment.StaffRole.MANAGER);
        findCheckboxGroup(view).setValue(Set.of(ManagerPermission.VIEW_REPORTS, ManagerPermission.PERSONNEL_MGMT));

        clickButton(view, "Offer role appointment");

        verify(presenter).offerRoleAppointment("Acme", targetId, StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.VIEW_REPORTS, ManagerPermission.PERSONNEL_MGMT));
        assertTrue(hasText(view, "Role appointment offer sent."));
    }

    @Test
    void GivenTargetAlreadyOwner_WhenOfferRoleAppointmentClicked_ThenFailureReasonFromServiceIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        UUID targetId = UUID.randomUUID();
        when(presenter.offerRoleAppointment(eq("Acme"), eq(targetId), eq(StaffAppointment.StaffRole.MANAGER), any()))
                .thenReturn(ActionResult.failure("Target is already an owner of this company"));
        when(presenter.loadOrganizationChart("Acme"))
                .thenReturn(OrgChartResult.success("Organization chart loaded.", List.of(personnel("manager", targetId))));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));
        selectTargetMember(view, "manager", targetId, StaffAppointment.StaffRole.MANAGER);

        clickButton(view, "Offer role appointment");

        verify(presenter).offerRoleAppointment(eq("Acme"), eq(targetId), eq(StaffAppointment.StaffRole.MANAGER), any());
        assertTrue(hasText(view, "Target is already an owner of this company"));
    }

    @Test
    void GivenOwnerDirectlyAppointedTarget_WhenOriginalOwnerClicksRevokePersonnel_ThenSuccessMessageIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        UUID targetId = UUID.randomUUID();
        when(presenter.revokePersonnel("Acme", targetId)).thenReturn(ActionResult.success("Personnel revoked."));
        when(presenter.loadOrganizationChart("Acme"))
                .thenReturn(OrgChartResult.success("Organization chart loaded.", List.of(personnel("manager", targetId))));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));
        selectTargetMember(view, "manager", targetId, StaffAppointment.StaffRole.MANAGER);

        clickButton(view, "Revoke personnel");

        verify(presenter).revokePersonnel("Acme", targetId);
        assertTrue(hasText(view, "Personnel revoked."));
    }

    @Test
    void GivenTargetAppointedByDifferentOwner_WhenOtherOwnerClicksRevokePersonnel_ThenOnlyAppointersCanRevokeMessageIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        UUID targetId = UUID.randomUUID();
        when(presenter.revokePersonnel("Acme", targetId)).thenReturn(ActionResult.failure(
                "Revoker does not have permission to revoke this member. Only the appointer can revoke their appointees."));
        when(presenter.loadOrganizationChart("Acme"))
                .thenReturn(OrgChartResult.success("Organization chart loaded.", List.of(personnel("manager", targetId))));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));
        selectTargetMember(view, "manager", targetId, StaffAppointment.StaffRole.MANAGER);

        clickButton(view, "Revoke personnel");

        verify(presenter).revokePersonnel("Acme", targetId);
        assertTrue(hasText(view, "Revoker does not have permission to revoke this member. Only the appointer can revoke their appointees."));
    }

    @Test
    void GivenOwnerMemberSession_WhenRelinquishOwnershipClicked_ThenPresenterIsCalledAndSuccessMessageIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.relinquishOwnership("Acme")).thenReturn(ActionResult.success("Ownership relinquished for Acme."));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));

        clickButton(view, "Relinquish ownership");

        verify(presenter).relinquishOwnership("Acme");
        assertTrue(hasText(view, "Ownership relinquished for Acme."));
    }

    @Test
    void GivenNonOwnerMemberSession_WhenRelinquishOwnershipClicked_ThenFailureMessageFromServiceIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.relinquishOwnership("Acme"))
                .thenReturn(ActionResult.failure("Only non-founder owners may relinquish ownership."));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));

        clickButton(view, "Relinquish ownership");

        verify(presenter).relinquishOwnership("Acme");
        assertTrue(hasText(view, "Only non-founder owners may relinquish ownership."));
    }

    // ── UI-23: Define & edit purchase/discount policies ─────────────

    @Test
    void GivenPolicyCompanySelected_WhenSetPurchasePolicyClicked_ThenSuccessMessageIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.setCompanyPurchasePolicy(eq("Acme"), any()))
                .thenReturn(ActionResult.success("Company purchase policy updated."));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Policy company name").setValue(company("Acme"));

        clickButton(view, "Set purchase policy");

        verify(presenter).setCompanyPurchasePolicy(eq("Acme"), any());
        assertTrue(hasText(view, "Company purchase policy updated."));
    }

    @Test
    void GivenInsufficientPermissions_WhenSetPurchasePolicyClicked_ThenFailureReasonIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.setCompanyPurchasePolicy(eq("Acme"), any()))
                .thenReturn(ActionResult.failure("Insufficient permissions: POLICY_MODIFICATION required"));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Policy company name").setValue(company("Acme"));

        clickButton(view, "Set purchase policy");

        verify(presenter).setCompanyPurchasePolicy(eq("Acme"), any());
        assertTrue(hasText(view, "Insufficient permissions: POLICY_MODIFICATION required"));
    }

    @Test
    void GivenPolicyCompanySelected_WhenSetDiscountPolicyClicked_ThenSuccessMessageIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.setCompanyDiscountPolicy(eq("Acme"), any()))
                .thenReturn(ActionResult.success("Company discount policy updated."));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Policy company name").setValue(company("Acme"));

        clickButton(view, "Set discount policy");

        verify(presenter).setCompanyDiscountPolicy(eq("Acme"), any());
        assertTrue(hasText(view, "Company discount policy updated."));
    }

    @Test
    void GivenInsufficientPermissions_WhenSetDiscountPolicyClicked_ThenFailureReasonIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        when(presenter.setCompanyDiscountPolicy(eq("Acme"), any()))
                .thenReturn(ActionResult.failure("Insufficient permissions: POLICY_MODIFICATION required"));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Policy company name").setValue(company("Acme"));

        clickButton(view, "Set discount policy");

        verify(presenter).setCompanyDiscountPolicy(eq("Acme"), any());
        assertTrue(hasText(view, "Insufficient permissions: POLICY_MODIFICATION required"));
    }

    private CompanyPresenter mockPresenter() {
        CompanyPresenter presenter = mock(CompanyPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Member (alice)");
        when(presenter.currentSessionState()).thenReturn(member());
        when(presenter.loadPersonnelAccess(any()))
                .thenReturn(PersonnelAccessResult.allowed("Owner personnel controls available."));
        when(presenter.loadLifecycleAccess(any()))
                .thenReturn(LifecycleAccessResult.allowed("Founder lifecycle controls available."));
        when(presenter.searchCompanies(any()))
                .thenReturn(List.of(company("Acme")));
        when(presenter.searchLookupCompanies(any()))
                .thenReturn(List.of(company("Acme")));
        when(presenter.searchLifecycleCompanies(any()))
                .thenReturn(List.of(company("Acme")));
        when(presenter.loadOrganizationChart(any()))
                .thenReturn(OrgChartResult.success("Organization chart loaded.", List.of()));
        return presenter;
    }

    private void fillCreateEventForm(CompanyView view) {
        findCompanyCombo(view, "Event company name").setValue(company("Acme"));
        findTextField(view, "Event name").setValue("Show");
        findTextArea(view, "Event description").setValue("desc");
        findDateTimePicker(view, "Start time").setValue(LocalDateTime.of(2026, 6, 1, 19, 0));
        findDateTimePicker(view, "End time").setValue(LocalDateTime.of(2026, 6, 1, 21, 0));
        findDateTimePicker(view, "Doors open time").setValue(LocalDateTime.of(2026, 6, 1, 18, 0));
        findIntegerField(view, "Lock minutes").setValue(15);
        findTextField(view, "Zone name").setValue("Floor");
        findBigDecimalField(view, "Zone price").setValue(new BigDecimal("50.00"));
        findIntegerField(view, "GA capacity").setValue(100);
        findTextField(view, "Venue section").setValue("Main Hall");
    }

    private static EventSummaryDTO eventSummary() {
        Instant start = Instant.parse("2026-06-01T19:00:00Z");
        return new EventSummaryDTO(
                UUID.randomUUID(),
                "Spring Concert",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plusSeconds(7200), start.minusSeconds(3600)),
                EventStatus.PUBLISHED
        );
    }

    private static EventMapDTO eventMap(UUID eventId, UUID zoneId) {
        return new EventMapDTO(
                eventId,
                "Spring Concert",
                "Acme",
                EventStatus.PUBLISHED,
                Map.of("Main Hall", zoneId),
                List.of(new EventMapDTO.ZoneInfo(zoneId, "Floor", ZoneType.GENERAL_ADMISSION,
                        new BigDecimal("50.00"), 100, 80, 20, List.of()))
        );
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

    private static OrgNodeDTO personnel(String username, UUID memberId) {
        return personnel(username, memberId, false);
    }

    private static OrgNodeDTO personnel(String username, UUID memberId, boolean revoked) {
        return new OrgNodeDTO(memberId, username, StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.VIEW_REPORTS), revoked, List.of());
    }

    private static String personnelLabel(String username, UUID memberId, StaffAppointment.StaffRole role) {
        return username + " | " + memberId + " | " + role;
    }

    private static List<String> targetMemberLabels(CompanyView view) {
        return findTargetMemberCombo(view).getDataProvider()
                .fetch(new Query<>())
                .map(String::valueOf)
                .toList();
    }

    private static List<String> companyNames(ComboBox<CompanySummaryDTO> combo) {
        return combo.getDataProvider()
                .fetch(new Query<>())
                .map(CompanySummaryDTO::name)
                .toList();
    }

    private static void selectTargetMember(
            CompanyView view,
            String username,
            UUID memberId,
            StaffAppointment.StaffRole role
    ) {
        String label = personnelLabel(username, memberId, role);
        Object option = findTargetMemberCombo(view).getDataProvider()
                .fetch(new Query<>())
                .filter(item -> label.equals(String.valueOf(item)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Target member option not found: " + label));
        findTargetMemberCombo(view).setValue(option);
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
                .anyMatch(CompanyViewTest::isEffectivelyVisible);
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

    @SuppressWarnings("unchecked")
    private static ComboBox<CompanySummaryDTO> findCompanyCombo(Component root, String label) {
        return (ComboBox<CompanySummaryDTO>) findComboByLabel(root, label);
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<EventSummaryDTO> findEventCombo(Component root, String label) {
        return (ComboBox<EventSummaryDTO>) findComboByLabel(root, label);
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<Object> findTargetMemberCombo(Component root) {
        return (ComboBox<Object>) findComboByLabel(root, "Target member");
    }

    private static ComboBox<?> findComboByLabel(Component root, String label) {
        return components(root).stream()
                .filter(ComboBox.class::isInstance)
                .map(component -> (ComboBox<?>) component)
                .filter(combo -> label.equals(combo.getLabel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ComboBox not found: " + label));
    }

    private static CompanySummaryDTO company(String name) {
        return new CompanySummaryDTO(name);
    }

    private static EventSummaryDTO event(String name, UUID id) {
        Instant start = Instant.parse("2026-06-01T19:00:00Z");
        return new EventSummaryDTO(
                id,
                name,
                EventCategory.CONCERT,
                new EventSchedule(start, start.plusSeconds(7200), start.minusSeconds(3600)),
                EventStatus.DRAFT
        );
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

    private static BigDecimalField findBigDecimalField(Component root, String label) {
        return components(root).stream()
                .filter(BigDecimalField.class::isInstance)
                .map(BigDecimalField.class::cast)
                .filter(field -> label.equals(field.getLabel()))
                .findFirst()
                .orElse(null);
    }

    private static DateTimePicker findDateTimePicker(Component root, String label) {
        return components(root).stream()
                .filter(DateTimePicker.class::isInstance)
                .map(DateTimePicker.class::cast)
                .filter(field -> label.equals(field.getLabel()))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static CheckboxGroup<ManagerPermission> findCheckboxGroup(Component root) {
        return components(root).stream()
                .filter(CheckboxGroup.class::isInstance)
                .map(component -> (CheckboxGroup<ManagerPermission>) component)
                .findFirst()
                .orElseThrow();
    }

    private static void selectTab(Component root, String label) {
        components(root).stream()
                .filter(Tab.class::isInstance)
                .map(Tab.class::cast)
                .filter(tab -> label.equals(tab.getLabel()))
                .findFirst()
                .orElseThrow()
                .getParent()
                .filter(com.vaadin.flow.component.tabs.Tabs.class::isInstance)
                .map(com.vaadin.flow.component.tabs.Tabs.class::cast)
                .orElseThrow()
                .setSelectedTab(components(root).stream()
                        .filter(Tab.class::isInstance)
                        .map(Tab.class::cast)
                        .filter(tab -> label.equals(tab.getLabel()))
                        .findFirst()
                        .orElseThrow());
    }

    private static Grid<EventSummaryDTO> findEventGrid(Component root) {
        return (Grid<EventSummaryDTO>) findGridById(root, "company-events-grid");
    }

    private static Grid<PurchaseRecordDTO> findPurchasesGrid(Component root) {
        return (Grid<PurchaseRecordDTO>) findGridById(root, "company-purchases-grid");
    }

    private static Grid<?> findGridById(Component root, String id) {
        return findGrids(root).stream()
                .filter(grid -> grid.getId().map(id::equals).orElse(false))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Grid not found: " + id));
    }

    private static List<String> columnHeaders(Grid<?> grid) {
        return grid.getColumns().stream()
                .map(Grid.Column::getHeaderText)
                .toList();
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

    private static Span findSpan(Component root, String text) {
        return components(root).stream()
                .filter(Span.class::isInstance)
                .map(Span.class::cast)
                .filter(span -> text.equals(span.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Span not found: " + text));
    }

    private static boolean containsText(Component root, String fragment) {
        return components(root).stream()
                .filter(HasText.class::isInstance)
                .map(HasText.class::cast)
                .map(HasText::getText)
                .filter(text -> text != null)
                .anyMatch(text -> text.contains(fragment));
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
                .map(CompanyViewTest::isEffectivelyVisible)
                .orElse(true);
    }

    private static void collect(Component component, List<Component> result) {
        result.add(component);
        component.getChildren().forEach(child -> collect(child, result));
    }

    private static SessionContext.UiState guest() {
        return new SessionContext.UiState(true, true, false, false, null, "Guest");
    }

    private static SessionContext.UiState member() {
        return new SessionContext.UiState(true, false, true, false, "alice", "Member");
    }
}
