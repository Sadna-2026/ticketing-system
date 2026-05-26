package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.ticketing.application.dto.CompanyPublicDTO;
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
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.Query;

@DisplayName("CompanyView")
class CompanyViewTest {

    @BeforeEach
    void setUp() {
        UI.setCurrent(new UI());
    }

    @AfterEach
    void tearDown() {
        UI.setCurrent(null);
    }

    @Test
    void GivenCompanyView_WhenRendered_ThenOwnerManagerCompanyEventLifecycleAndReportingControlsExist() {
        CompanyView view = new CompanyView(mockPresenter());

        assertTrue(hasButton(view, "Open company"));
        assertTrue(hasButton(view, "Load company info"));
        assertTrue(hasButton(view, "Offer role appointment"));
        assertTrue(hasButton(view, "Accept role offer"));
        assertTrue(hasButton(view, "Reject role offer"));
        assertTrue(hasButton(view, "Revoke personnel"));
        assertTrue(hasButton(view, "Change manager permissions"));
        assertTrue(hasButton(view, "Create company event"));
        assertTrue(hasButton(view, "Edit event details"));
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
        assertNotNull(findTextField(view, "Event ID"));
        assertEquals(2, findGrids(view).size());
    }

    @Test
    void GivenCompanyName_WhenLoadingCompanyInfo_ThenCompanyDetailsAndEventsAreDisplayed() {
        CompanyPresenter presenter = mockPresenter();
        EventSummaryDTO event = eventSummary();
        when(presenter.loadCompanyInfo("Acme"))
                .thenReturn(CompanyInfoResult.success("Company information loaded.",
                        new CompanyPublicDTO("Acme", "desc", List.of(event))));
        CompanyView view = new CompanyView(presenter);
        findTextField(view, "Company info name").setValue("Acme");

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
        findTextField(view, "Personnel company name").setValue("Acme");
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
        when(presenter.createEvent(eq("Acme"), eq("Show"), eq("desc"), eq(EventCategory.CONCERT),
                any(), any(), any(), eq(15), eq("Floor"), eq(new BigDecimal("50.00")), eq(100), eq("Main Hall")))
                .thenReturn(EventActionResult.created("Event created.", eventId));
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
        assertEquals(eventId.toString(), findTextField(view, "Event ID").getValue());
        assertEquals(eventId.toString(), findTextField(view, "Inventory event ID").getValue());

        findTextField(view, "Inventory zone ID").setValue(zoneId.toString());
        findTextField(view, "Seat row").setValue("A");
        findTextField(view, "Seat number").setValue("1");
        findTextField(view, "Seat ID").setValue(seatId.toString());
        findIntegerField(view, "Capacity delta").setValue(5);
        findBigDecimalField(view, "Zone price update").setValue(new BigDecimal("75.00"));

        clickButton(view, "Cancel event");
        clickButton(view, "Load event map");
        clickButton(view, "Add seat");
        clickButton(view, "Remove seat");
        clickButton(view, "Increase GA capacity");
        clickButton(view, "Decrease GA capacity");
        clickButton(view, "Set zone price");

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
        findTextField(view, "Lifecycle company name").setValue("Acme");
        findTextField(view, "Reporting company name").setValue("Acme");

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

    private CompanyPresenter mockPresenter() {
        CompanyPresenter presenter = mock(CompanyPresenter.class);
        when(presenter.currentSessionLabel()).thenReturn("Current session: Member (alice)");
        return presenter;
    }

    private void fillCreateEventForm(CompanyView view) {
        findTextField(view, "Event company name").setValue("Acme");
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
        return findGrids(root).stream()
                .map(grid -> (Grid<EventSummaryDTO>) grid)
                .findFirst()
                .orElseThrow();
    }

    private static Grid<PurchaseRecordDTO> findPurchasesGrid(Component root) {
        List<Grid<?>> grids = findGrids(root);
        return (Grid<PurchaseRecordDTO>) grids.get(1);
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

    private static void collect(Component component, List<Component> result) {
        result.add(component);
        component.getChildren().forEach(child -> collect(child, result));
    }
}
