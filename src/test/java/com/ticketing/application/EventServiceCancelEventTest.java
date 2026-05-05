package com.ticketing.application;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.Interface.IActiveOrderRepository;
import com.ticketing.infrastructure.Interface.IEventRepository;

public class EventServiceCancelEventTest {

    private static final String COMPANY_NAME = "Acme Productions";
    private static final String TOKEN = "valid-token";

    private IEventRepository eventRepo;
    private ICompanyRepository companyRepo;
    private IMemberRepository memberRepo;
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
        tokens = mock(ISessionTokenService.class);
        eventService = new EventService(eventRepo, companyRepo, memberRepo,
                mock(IActiveOrderRepository.class), tokens);

        memberId = UUID.randomUUID();
        member = new Member(memberId, "owner1", "owner1@example.com", "pw");
        memberRepo.save(member);

        Company company = new Company(COMPANY_NAME, "desc", memberId);
        companyRepo.save(company);

        // seed a DRAFT event for the company
        eventId = UUID.randomUUID();
        Event event = newDraftEvent(eventId);
        eventRepo.save(event);

        when(tokens.isValid(TOKEN)).thenReturn(true);
        when(tokens.extractMemberId(TOKEN)).thenReturn(memberId);
    }

    @Test
    public void GivenOwner_WhenCancelEvent_ThenStatusBecomesCancelled() {
        addStaff(StaffAppointment.StaffRole.OWNER, Set.of());

        eventService.cancelEvent(TOKEN, eventId);

        Event saved = eventRepo.findById(eventId).orElseThrow();
        assertEquals(EventStatus.CANCELLED, saved.getStatus());
    }

    @Test
    public void GivenPublishedEvent_WhenCancelEvent_ThenStatusBecomesCancelled() {
        // promote the seeded DRAFT to PUBLISHED so we exercise the non-DRAFT path
        Event seeded = eventRepo.findById(eventId).orElseThrow();
        seeded.addZone(com.ticketing.domain.event.InventoryZone.createGA(
                UUID.randomUUID(), "Floor", new java.math.BigDecimal("50.00"), 100));
        seeded.publish();
        eventRepo.save(seeded);

        addStaff(StaffAppointment.StaffRole.OWNER, Set.of());
        eventService.cancelEvent(TOKEN, eventId);

        assertEquals(EventStatus.CANCELLED,
                eventRepo.findById(eventId).orElseThrow().getStatus());
    }

    @Test
    public void GivenManagerWithEventLifecycle_WhenCancelEvent_ThenSucceed() {
        addStaff(StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.EVENT_LIFECYCLE));

        eventService.cancelEvent(TOKEN, eventId);

        assertEquals(EventStatus.CANCELLED,
                eventRepo.findById(eventId).orElseThrow().getStatus());
    }

    @Test
    public void GivenManagerWithoutEventLifecycle_WhenCancelEvent_ThenThrowSecurityException() {
        addStaff(StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.MAP_DEFINITION, ManagerPermission.INVENTORY_MGMT));

        assertThrows(SecurityException.class,
                () -> eventService.cancelEvent(TOKEN, eventId));
    }

    @Test
    public void GivenAlreadyCancelledEvent_WhenCancelEvent_ThenThrowIllegalStateException() {
        addStaff(StaffAppointment.StaffRole.OWNER, Set.of());
        eventService.cancelEvent(TOKEN, eventId);

        assertThrows(IllegalStateException.class,
                () -> eventService.cancelEvent(TOKEN, eventId));
    }

    @Test
    public void GivenUnknownEventId_WhenCancelEvent_ThenThrowIllegalArgumentException() {
        addStaff(StaffAppointment.StaffRole.OWNER, Set.of());

        assertThrows(IllegalArgumentException.class,
                () -> eventService.cancelEvent(TOKEN, UUID.randomUUID()));
    }

    @Test
    public void GivenNullEventId_WhenCancelEvent_ThenThrowIllegalArgumentException() {
        addStaff(StaffAppointment.StaffRole.OWNER, Set.of());

        assertThrows(IllegalArgumentException.class,
                () -> eventService.cancelEvent(TOKEN, null));
    }

    @Test
    public void GivenGuestToken_WhenCancelEvent_ThenThrowSecurityException() {
        when(tokens.extractMemberId(TOKEN)).thenReturn(null);

        assertThrows(SecurityException.class,
                () -> eventService.cancelEvent(TOKEN, eventId));
    }

    @Test
    public void GivenStaffOfOtherCompany_WhenCancelEvent_ThenThrowSecurityException() {
        // member has an appointment but for a *different* company
        member.addStaffAppointment("Other Co",
                new StaffAppointment("Other Co", memberId,
                        StaffAppointment.StaffRole.OWNER, Set.of()));
        memberRepo.save(member);

        assertThrows(SecurityException.class,
                () -> eventService.cancelEvent(TOKEN, eventId));
    }

    // helpers

    private void addStaff(StaffAppointment.StaffRole role, Set<ManagerPermission> perms) {
        StaffAppointment a = new StaffAppointment(COMPANY_NAME, memberId, role, perms);
        member.addStaffAppointment(COMPANY_NAME, a);
        memberRepo.save(member);
    }

    private static Event newDraftEvent(UUID id) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        EventSchedule schedule = new EventSchedule(
                start, start.plus(3, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS));
        return new Event(id, COMPANY_NAME, "Spring Concert", null,
                EventCategory.CONCERT, schedule, new LockTimerDuration(Duration.ofMinutes(15)));
    }
}
