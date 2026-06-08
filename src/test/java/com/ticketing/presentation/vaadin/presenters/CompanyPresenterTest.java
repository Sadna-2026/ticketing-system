package com.ticketing.presentation.vaadin.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import com.ticketing.application.CreateEventRequest;
import com.ticketing.application.EditEventRequest;
import com.ticketing.application.dto.CompanyPublicDTO;
import com.ticketing.application.dto.CompanySummaryDTO;
import com.ticketing.application.dto.EventDetailsDTO;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.application.dto.OrgNodeDTO;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.SalesReportDTO;
import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.CompletedPurchaseService;
import com.ticketing.application.services.EventService;
import com.ticketing.application.services.MemberService;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.ZoneType;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.CompanyInfoResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventMapResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.OrgChartResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.PurchaseHistoryResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.SalesReportResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;

@DisplayName("CompanyPresenter")
@ExtendWith(VaadinSessionExtension.class)
class CompanyPresenterTest {

    private CompanyService companyService;
    private MemberService memberService;
    private EventService eventService;
    private CompletedPurchaseService completedPurchaseService;
    private CompanyPresenter presenter;

    @BeforeEach
    void setUp() {
        companyService = mock(CompanyService.class);
        memberService = mock(MemberService.class);
        eventService = mock(EventService.class);
        completedPurchaseService = mock(CompletedPurchaseService.class);
        presenter = new CompanyPresenter(
                companyService,
                memberService,
                eventService,
                completedPurchaseService
        );
    }

    @Test
    void GivenMemberSession_WhenOpeningCompany_ThenCompanyServiceIsCalledWithSessionToken() {
        memberSession();
        when(companyService.openProductionCompany("member-token", "Acme", "desc")).thenReturn("Acme");

        ActionResult result = presenter.openCompany(" Acme ", " desc ");

        assertTrue(result.success());
        assertEquals("Company opened: Acme", result.message());
        verify(companyService).openProductionCompany("member-token", "Acme", "desc");
    }

    @Test
    void GivenBlankCompanyName_WhenOpeningCompany_ThenValidationMessageIsReturnedBeforeServiceCall() {
        memberSession();

        ActionResult result = presenter.openCompany("   ", "desc");

        assertFalse(result.success());
        assertEquals("Company name is required.", result.message());
        verifyNoInteractions(companyService);
    }

    @Test
    void GivenCompanyName_WhenLoadingCompanyInfo_ThenQueryServiceIsCalledWithoutSessionToken() {
        CompanyPublicDTO company = new CompanyPublicDTO("Acme", "desc", List.of(eventSummary()));
        when(companyService.getCompanyInfo("Acme")).thenReturn(Optional.of(company));

        CompanyInfoResult result = presenter.loadCompanyInfo("Acme");

        assertTrue(result.success());
        assertSame(company, result.company());
        verify(companyService).getCompanyInfo("Acme");
    }

    @Test
    void GivenPersonnelInputs_WhenManagingRoles_ThenCompanyAndMemberServicesAreCalledDirectly() {
        memberSession();
        UUID targetId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        OrgNodeDTO root = new OrgNodeDTO(targetId, "bob", StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.VIEW_REPORTS), false, List.of());
        when(memberService.getOrganizationChart("member-token", "Acme")).thenReturn(List.of(root));

        ActionResult offer = presenter.offerRoleAppointment("Acme", targetId, StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.VIEW_REPORTS));
        ActionResult accept = presenter.respondToRoleOffer(offerId, true);
        ActionResult revoke = presenter.revokePersonnel("Acme", targetId);
        ActionResult permissions = presenter.changeManagerPermissions("Acme", targetId,
                Set.of(ManagerPermission.PERSONNEL_MGMT));
        OrgChartResult chart = presenter.loadOrganizationChart("Acme");

        assertTrue(offer.success());
        assertTrue(accept.success());
        assertTrue(revoke.success());
        assertTrue(permissions.success());
        assertTrue(chart.success());
        assertEquals(List.of(root), chart.roots());
        verify(companyService).offerRoleAppointment("member-token", "Acme", targetId,
                StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.VIEW_REPORTS));
        verify(companyService).respondToRoleAppointment("member-token", offerId, true);
        verify(companyService).revokePersonnel("member-token", "Acme", targetId);
        verify(companyService).changeManagerPermissions("member-token", "Acme", targetId,
                Set.of(ManagerPermission.PERSONNEL_MGMT));
        verify(memberService).getOrganizationChart("member-token", "Acme");
    }

    @Test
    void GivenEventInputs_WhenCreatingEditingPublishingAndCancellingEvents_ThenEventServiceIsCalledDirectly() {
        memberSession();
        UUID eventId = UUID.randomUUID();
        Instant start = Instant.parse("2026-06-01T19:00:00Z");
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Instant doors = start.minus(1, ChronoUnit.HOURS);
        EventDetailsDTO edited = new EventDetailsDTO(eventId, "New name", "Acme", "desc", "artist",
                EventCategory.CONCERT, new EventSchedule(start, end, doors), EventStatus.DRAFT);
        when(eventService.createEvent(eq("member-token"), any(CreateEventRequest.class))).thenReturn(eventId);
        when(eventService.editEvent(eq("member-token"), any(EditEventRequest.class))).thenReturn(edited);

        EventActionResult created = presenter.createEvent(" Acme ", " Show ", "desc", EventCategory.CONCERT,
                start, end, doors, 15, " Floor ", new BigDecimal("50.00"), 100, " Main Hall ");
        EventActionResult edit = presenter.editEvent(eventId, "New name", "desc", "artist", start, end, doors);
        ActionResult publish = presenter.publishEvent(eventId);
        ActionResult cancel = presenter.cancelEvent(eventId);

        ArgumentCaptor<CreateEventRequest> createRequest = ArgumentCaptor.forClass(CreateEventRequest.class);
        ArgumentCaptor<EditEventRequest> editRequest = ArgumentCaptor.forClass(EditEventRequest.class);
        verify(eventService).createEvent(eq("member-token"), createRequest.capture());
        verify(eventService).editEvent(eq("member-token"), editRequest.capture());
        verify(eventService).publishEvent("member-token", eventId);
        verify(eventService).cancelEvent("member-token", eventId);
        assertTrue(created.success());
        assertEquals(eventId, created.eventId());
        assertEquals("Acme", createRequest.getValue().companyName());
        assertEquals("Show", createRequest.getValue().name());
        assertEquals("Floor", createRequest.getValue().zones().get(0).name());
        assertEquals(Map.of("Main Hall", "Floor"), createRequest.getValue().sectionToZoneName());
        assertTrue(edit.success());
        assertSame(edited, edit.eventDetails());
        assertEquals(eventId, editRequest.getValue().eventId());
        assertTrue(publish.success());
        assertTrue(cancel.success());
    }

    @Test
    void GivenBlankCreateEventIdentifier_WhenCreatingEvent_ThenValidationMessageIsReturnedBeforeServiceCall() {
        memberSession();
        Instant start = Instant.parse("2026-06-01T19:00:00Z");

        EventActionResult result = presenter.createEvent("Acme", "Show", "desc", EventCategory.CONCERT,
                start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS),
                15, "   ", new BigDecimal("50.00"), 100, "Main Hall");

        assertFalse(result.success());
        assertEquals("Zone name is required.", result.message());
        verifyNoInteractions(eventService);
    }

    @Test
    void GivenInventoryInputs_WhenUsingSupportedInventoryActions_ThenEventServicesAreCalled() {
        memberSession();
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        EventMapDTO map = eventMap(eventId, zoneId);
        when(eventService.getEventMap(eventId)).thenReturn(Optional.of(map));

        EventMapResult mapResult = presenter.loadEventMap(eventId);
        ActionResult addSeat = presenter.addSeat(eventId, zoneId, " A ", " 1 ");
        ActionResult removeSeat = presenter.removeSeat(eventId, zoneId, seatId);
        ActionResult increase = presenter.increaseGACapacity(eventId, zoneId, 5);
        ActionResult decrease = presenter.decreaseGACapacity(eventId, zoneId, 2);
        ActionResult price = presenter.setZonePrice(eventId, zoneId, new BigDecimal("75.00"));

        assertTrue(mapResult.success());
        assertSame(map, mapResult.eventMap());
        assertTrue(addSeat.success());
        assertTrue(removeSeat.success());
        assertTrue(increase.success());
        assertTrue(decrease.success());
        assertTrue(price.success());
        verify(eventService).getEventMap(eventId);
        verify(eventService).addSeatsToZone("member-token", eventId, zoneId, List.of(new CreateEventRequest.SeatSpec("A", "1")));
        verify(eventService).removeSeats("member-token", eventId, zoneId, List.of(seatId));
        verify(eventService).increaseGACapacity("member-token", eventId, zoneId, 5);
        verify(eventService).decreaseGACapacity("member-token", eventId, zoneId, 2);
        verify(eventService).setZonePrice("member-token", eventId, zoneId, new BigDecimal("75.00"));
    }

    @Test
    void GivenBlankSeatFields_WhenAddingSeat_ThenValidationMessageIsReturnedBeforeServiceCall() {
        memberSession();

        ActionResult result = presenter.addSeat(UUID.randomUUID(), UUID.randomUUID(), "A", "   ");

        assertFalse(result.success());
        assertEquals("Seat number is required.", result.message());
        verifyNoInteractions(eventService);
    }

    @Test
    void GivenLifecycleAndReportingInputs_WhenLoadingData_ThenLifecycleAndReportingServicesAreCalled() {
        memberSession();
        UUID memberId = SessionContext.getMemberId();
        PurchaseRecordDTO purchase = purchase(memberId);
        SalesReportDTO report = new SalesReportDTO("Acme", memberId, List.of(purchase), new BigDecimal("80.00"), 1);
        when(companyService.getPurchaseHistory("member-token", "Acme")).thenReturn(List.of(purchase));
        when(completedPurchaseService.getHierarchicalSalesReport("member-token", "Acme")).thenReturn(report);

        ActionResult suspend = presenter.suspendCompany("Acme");
        ActionResult reopen = presenter.reopenCompany("Acme");
        ActionResult close = presenter.closeCompany("Acme");
        PurchaseHistoryResult history = presenter.loadPurchaseHistory("Acme");
        SalesReportResult sales = presenter.loadSalesReport("Acme");

        assertTrue(suspend.success());
        assertTrue(reopen.success());
        assertTrue(close.success());
        assertTrue(history.success());
        assertEquals(List.of(purchase), history.purchases());
        assertTrue(sales.success());
        assertSame(report, sales.report());
        verify(companyService).suspendCompany("member-token", "Acme");
        verify(companyService).reopenCompany("member-token", "Acme");
        verify(companyService).permanentCloseByFounder("member-token", "Acme");
        verify(companyService).getPurchaseHistory("member-token", "Acme");
        verify(completedPurchaseService).getHierarchicalSalesReport("member-token", "Acme");
    }

    @Test
    void GivenMissingMemberSession_WhenCompanyActionRuns_ThenNoServiceIsCalledAndUserMessageIsReturned() {
        SessionContext.setSessionToken("guest-token");

        ActionResult result = presenter.openCompany("Acme", "desc");

        assertFalse(result.success());
        assertEquals("Log in as a member before using company owner or manager actions.", result.message());
        verifyNoInteractions(companyService);
    }

    @Test
    void GivenApplicationServiceThrows_WhenCompanyActionRuns_ThenApplicationMessageIsReturned() {
        memberSession();
        when(companyService.openProductionCompany("member-token", "Acme", "desc"))
                .thenThrow(new IllegalArgumentException("A production company with this name already exists."));

        ActionResult result = presenter.openCompany("Acme", "desc");

        assertFalse(result.success());
        assertEquals("A production company with this name already exists.", result.message());
        assertNull(presenter.loadEventMap(null).eventMap());
    }

    @Test
    void GivenCompaniesExist_WhenSearchingCompanies_ThenServiceResultsAreReturned() {
        when(companyService.searchCompanies("ac")).thenReturn(List.of(new CompanySummaryDTO("Acme")));

        assertEquals(List.of(new CompanySummaryDTO("Acme")), presenter.searchCompanies("ac"));
    }

    @Test
    void GivenServiceError_WhenSearchingCompanies_ThenEmptyListIsReturned() {
        when(companyService.searchCompanies(any())).thenThrow(new IllegalStateException("boom"));

        assertTrue(presenter.searchCompanies("a").isEmpty());
    }

    @Test
    void GivenMemberSession_WhenListingCompanyEvents_ThenEventServiceIsCalledWithToken() {
        memberSession();
        EventSummaryDTO event = eventSummary();
        when(eventService.listCompanyEvents("member-token", "Acme")).thenReturn(List.of(event));

        assertEquals(List.of(event), presenter.listCompanyEvents("Acme"));
    }

    @Test
    void GivenNoMemberSession_WhenListingCompanyEvents_ThenEmptyListIsReturnedWithoutCallingService() {
        when(eventService.listCompanyEvents(any(), any())).thenReturn(List.of(eventSummary()));

        assertTrue(presenter.listCompanyEvents("Acme").isEmpty());
    }

    @Test
    void GivenBlankCompany_WhenListingCompanyEvents_ThenEmptyListIsReturned() {
        memberSession();

        assertTrue(presenter.listCompanyEvents("   ").isEmpty());
    }

    @Test
    void GivenMemberSession_WhenRelinquishingOwnership_ThenCompanyServiceIsCalledWithTokenAndCompanyName() {
        memberSession();

        ActionResult result = presenter.relinquishOwnership("Acme");

        assertTrue(result.success());
        assertEquals("Ownership relinquished for Acme.", result.message());
        verify(companyService).relinquishOwnership("member-token", "Acme");
    }

    @Test
    void GivenBlankCompanyName_WhenRelinquishingOwnership_ThenValidationFailureIsReturnedBeforeServiceCall() {
        memberSession();

        ActionResult result = presenter.relinquishOwnership("   ");

        assertFalse(result.success());
        assertEquals("Company name is required.", result.message());
        verifyNoInteractions(companyService);
    }

    @Test
    void GivenBrowsableEvents_WhenSearchingBrowsable_ThenSearchServiceResultsAreReturned() {
        EventSummaryDTO event = eventSummary();
        when(eventService.searchEvents(any())).thenReturn(List.of(event));

        assertEquals(List.of(event), presenter.searchBrowsableEvents());
    }

    private void memberSession() {
        UUID memberId = UUID.randomUUID();
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(memberId);
        SessionContext.setUsername("alice");
    }

    private static EventSummaryDTO eventSummary() {
        Instant start = Instant.parse("2026-06-01T19:00:00Z");
        return new EventSummaryDTO(
                UUID.randomUUID(),
                "Spring Concert",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS)),
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
                List.of(new EventMapDTO.ZoneInfo(
                        zoneId,
                        "Floor",
                        ZoneType.GENERAL_ADMISSION,
                        new BigDecimal("50.00"),
                        100,
                        80,
                        20,
                        List.of()
                ))
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
}
