package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
        assertTrue(hasButton(view, "Close company"));
        assertTrue(hasButton(view, "Load company purchase history"));
        assertTrue(hasButton(view, "Load sales report"));
        assertNotNull(findTextField(view, "New company name"));
        assertNotNull(findTextField(view, "Target member ID"));
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
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));
        findTextField(view, "Target member ID").setValue(targetId.toString());
        findTextField(view, "Role offer ID").setValue(offerId.toString());
        findCheckboxGroup(view).setValue(Set.of(ManagerPermission.VIEW_REPORTS));

        clickButton(view, "Offer role appointment");
        clickButton(view, "Accept role offer");
        clickButton(view, "Reject role offer");
        clickButton(view, "Revoke personnel");
        clickButton(view, "Change manager permissions");

        verify(presenter).offerRoleAppointment("Acme", targetId, StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.VIEW_REPORTS));
        verify(presenter).respondToRoleOffer(offerId, true);
        verify(presenter).respondToRoleOffer(offerId, false);
        verify(presenter).revokePersonnel("Acme", targetId);
        verify(presenter).changeManagerPermissions("Acme", targetId, Set.of(ManagerPermission.VIEW_REPORTS));
        assertTrue(hasText(view, "Manager permissions updated."));
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
        when(presenter.closeCompany("Acme")).thenReturn(ActionResult.success("Company closed."));
        when(presenter.loadPurchaseHistory("Acme")).thenReturn(PurchaseHistoryResult.success("Loaded 1 purchase(s).", List.of(purchase)));
        when(presenter.loadSalesReport("Acme")).thenReturn(SalesReportResult.success("Sales report loaded.", report));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Lifecycle company name").setValue(company("Acme"));
        findCompanyCombo(view, "Reporting company name").setValue(company("Acme"));

        clickButton(view, "Suspend company");
        clickButton(view, "Reopen company");
        clickButton(view, "Close company");
        clickButton(view, "Load company purchase history");
        clickButton(view, "Load sales report");

        assertTrue(hasText(view, "Company closed."));
        assertTrue(hasText(view, "Total purchases: 1"));
        assertTrue(hasText(view, "Total revenue: 80.00"));
        assertEquals(List.of(purchase), findPurchasesGrid(view).getDataProvider().fetch(new Query<>()).toList());
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
        assertTrue(findTextField(view, "Target member ID").isRequiredIndicatorVisible());
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
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));
        findTextField(view, "Target member ID").setValue(targetId.toString());
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
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));
        findTextField(view, "Target member ID").setValue(targetId.toString());

        clickButton(view, "Offer role appointment");

        verify(presenter).offerRoleAppointment(eq("Acme"), eq(targetId), eq(StaffAppointment.StaffRole.MANAGER), any());
        assertTrue(hasText(view, "Target is already an owner of this company"));
    }

    @Test
    void GivenOwnerDirectlyAppointedTarget_WhenOriginalOwnerClicksRevokePersonnel_ThenSuccessMessageIsDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        UUID targetId = UUID.randomUUID();
        when(presenter.revokePersonnel("Acme", targetId)).thenReturn(ActionResult.success("Personnel revoked."));
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));
        findTextField(view, "Target member ID").setValue(targetId.toString());

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
        CompanyView view = new CompanyView(presenter);
        findCompanyCombo(view, "Personnel company name").setValue(company("Acme"));
        findTextField(view, "Target member ID").setValue(targetId.toString());

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

    private CompanyPresenter mockPresenter() {
        CompanyPresenter presenter = mock(CompanyPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Member (alice)");
        when(presenter.currentSessionState()).thenReturn(member());
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
