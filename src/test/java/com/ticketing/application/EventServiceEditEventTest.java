package com.ticketing.application;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;

public class EventServiceEditEventTest {

    private static final String COMPANY = "Acme Productions";
    private static final String TOKEN = "valid-token";

    private InMemoryEventRepository eventRepo;
    private InMemoryCompanyRepository companyRepo;
    private InMemoryMemberRepository memberRepo;
    private IOrderRepository orderRepo;
    private ISessionTokenService tokens;
    private EventService eventService;

    private UUID memberId;
    private Member member;
    private UUID eventId;

    @BeforeEach
    public void setUp() {
        eventRepo = new InMemoryEventRepository();
        companyRepo = new InMemoryCompanyRepository();
        memberRepo = new InMemoryMemberRepository();
        orderRepo = mock(IOrderRepository.class);
        tokens = mock(ISessionTokenService.class);
        when(orderRepo.findActiveByEventId(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of()); // no active orders by default
        eventService = new EventService(eventRepo, companyRepo, memberRepo, orderRepo, tokens);

        memberId = UUID.randomUUID();
        member = new Member(memberId, "owner", "owner@x.com", "pw");
        memberRepo.save(member);

        Company company = new Company(COMPANY, "desc", memberId);
        companyRepo.save(company);

        eventId = UUID.randomUUID();
        eventRepo.save(makeDraft(eventId, "Original Name", "Original Artist"));

        when(tokens.isValid(TOKEN)).thenReturn(true);
        when(tokens.extractMemberId(TOKEN)).thenReturn(memberId);
    }

    @Test
    public void GivenOwner_WhenEditEvent_ThenFieldsUpdatedAndDtoReturned() {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());
        EditEventRequest req = new EditEventRequest(eventId, "New Name",
                "Updated description", "New Artist", null);

        EventDetailsDTO dto = eventService.editEvent(TOKEN, req);

        assertNotNull(dto);
        assertEquals("New Name", dto.name());
        assertEquals("Updated description", dto.description());
        assertEquals("New Artist", dto.artist());
        // and the event in the repo reflects the change
        Event saved = eventRepo.findById(eventId).orElseThrow();
        assertEquals("New Name", saved.getName());
        assertEquals("New Artist", saved.getArtist());
    }

    @Test
    public void GivenOwner_WhenEditScheduleOnly_ThenScheduleUpdated() {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());
        Instant newStart = Instant.now().plus(60, ChronoUnit.DAYS);
        EventSchedule newSchedule = new EventSchedule(
                newStart, newStart.plus(2, ChronoUnit.HOURS), newStart.minus(30, ChronoUnit.MINUTES));
        EditEventRequest req = new EditEventRequest(eventId, null, null, null, newSchedule);

        EventDetailsDTO dto = eventService.editEvent(TOKEN, req);

        assertEquals(newStart, dto.schedule().getStartTime());
    }

    @Test
    public void GivenManagerWithBothPermissions_WhenEditEvent_ThenSucceed() {
        appoint(StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.MAP_DEFINITION, ManagerPermission.INVENTORY_MGMT));
        EditEventRequest req = new EditEventRequest(eventId, "Renamed", null, null, null);

        EventDetailsDTO dto = eventService.editEvent(TOKEN, req);

        assertEquals("Renamed", dto.name());
    }

    @Test
    public void GivenManagerMissingMapDefinition_WhenEditEvent_ThenThrowSecurityException() {
        appoint(StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.INVENTORY_MGMT));
        EditEventRequest req = new EditEventRequest(eventId, "X", null, null, null);

        assertThrows(SecurityException.class,
                () -> eventService.editEvent(TOKEN, req));
    }

    @Test
    public void GivenManagerMissingInventoryMgmt_WhenEditEvent_ThenThrowSecurityException() {
        appoint(StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.MAP_DEFINITION));
        EditEventRequest req = new EditEventRequest(eventId, "X", null, null, null);

        assertThrows(SecurityException.class,
                () -> eventService.editEvent(TOKEN, req));
    }

    @Test
    public void GivenEventWithActiveReservations_WhenEditEvent_ThenThrowIllegalStateException() {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());
        // simulate: there's one active order pointing at our event
        ActiveOrder pendingOrder = new ActiveOrder(
                UUID.randomUUID(), UUID.randomUUID(), eventId, Instant.now());
        when(orderRepo.findActiveByEventId(eventId)).thenReturn(List.of(pendingOrder));

        EditEventRequest req = new EditEventRequest(eventId, "Whatever", null, null, null);
        assertThrows(IllegalStateException.class,
                () -> eventService.editEvent(TOKEN, req));
    }

    @Test
    public void GivenSuspendedCompany_WhenEditEvent_ThenThrowIllegalStateException() {
        // build a separate company, suspend it via the lifecycle aggregate, then attach an event
        Company suspended = new Company("Suspended Co", "x", memberId);
        suspended.suspend();
        companyRepo.save(suspended);
        UUID otherEventId = UUID.randomUUID();
        eventRepo.save(makeDraftFor(otherEventId, "Suspended Co", "Some Event", "Artist"));

        // give the member an Owner appointment for the suspended company
        member.addStaffAppointment("Suspended Co", new StaffAppointment(
                "Suspended Co", memberId, StaffAppointment.StaffRole.OWNER, Set.of()));
        memberRepo.save(member);

        EditEventRequest req = new EditEventRequest(otherEventId, "Try", null, null, null);
        assertThrows(IllegalStateException.class,
                () -> eventService.editEvent(TOKEN, req));
    }

    @Test
    public void GivenUnknownEventId_WhenEditEvent_ThenThrowIllegalArgumentException() {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());
        EditEventRequest req = new EditEventRequest(UUID.randomUUID(), "X", null, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> eventService.editEvent(TOKEN, req));
    }

    @Test
    public void GivenGuestToken_WhenEditEvent_ThenThrowSecurityException() {
        when(tokens.extractMemberId(TOKEN)).thenReturn(null);
        EditEventRequest req = new EditEventRequest(eventId, "X", null, null, null);

        assertThrows(SecurityException.class,
                () -> eventService.editEvent(TOKEN, req));
    }

    @Test
    public void GivenStaffOfOtherCompany_WhenEditEvent_ThenThrowSecurityException() {
        member.addStaffAppointment("Other Co", new StaffAppointment(
                "Other Co", memberId, StaffAppointment.StaffRole.OWNER, Set.of()));
        memberRepo.save(member);
        EditEventRequest req = new EditEventRequest(eventId, "X", null, null, null);

        assertThrows(SecurityException.class,
                () -> eventService.editEvent(TOKEN, req));
    }

    @Test
    public void GivenAllNullFields_WhenEditEvent_ThenNoOpReturnsCurrentDto() {
        appoint(StaffAppointment.StaffRole.OWNER, Set.of());
        EditEventRequest req = new EditEventRequest(eventId, null, null, null, null);

        EventDetailsDTO dto = eventService.editEvent(TOKEN, req);

        assertEquals("Original Name", dto.name());
        assertEquals("Original Artist", dto.artist());
    }

    @Test
    public void GivenBlankNameInRequest_WhenBuildRequest_ThenThrowIllegalArgumentException() {
        // request-level guard: blank name is rejected at construction time
        assertThrows(IllegalArgumentException.class,
                () -> new EditEventRequest(eventId, "  ", null, null, null));
    }

    @Test
    public void GivenNullEventId_WhenBuildRequest_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new EditEventRequest(null, "X", null, null, null));
    }

    // helpers

    private void appoint(StaffAppointment.StaffRole role, Set<ManagerPermission> perms) {
        member.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, memberId, role, perms));
        memberRepo.save(member);
    }

    private static Event makeDraft(UUID id, String name, String artist) {
        return makeDraftFor(id, COMPANY, name, artist);
    }

    private static Event makeDraftFor(UUID id, String companyName, String name, String artist) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        EventSchedule s = new EventSchedule(
                start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS));
        Event e = new Event(id, companyName, name, "desc", EventCategory.CONCERT, s,
                new LockTimerDuration(Duration.ofMinutes(15)));
        e.setArtist(artist);
        return e;
    }
}
