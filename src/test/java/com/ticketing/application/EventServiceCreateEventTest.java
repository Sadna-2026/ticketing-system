package com.ticketing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.CompanyStatus;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.VenueMap;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.Interface.IEventRepository;

public class EventServiceCreateEventTest {

    private static final String COMPANY_NAME = "Acme Productions";
    private static final String VALID_TOKEN = "valid-token";

    private InMemoryEventRepository eventRepository;
    private InMemoryCompanyRepository companyRepository;
    private InMemoryMemberRepository memberRepository;
    private ISessionTokenService sessionTokenService;
    private EventService eventService;

    private UUID memberId;
    private Member member;
    private Company company;

    @BeforeEach
    public void setUp() {
        eventRepository = new InMemoryEventRepository();
        companyRepository = new InMemoryCompanyRepository();
        memberRepository = new InMemoryMemberRepository();
        sessionTokenService = mock(ISessionTokenService.class);

        eventService = new EventService(
                eventRepository, companyRepository, memberRepository,
                mock(IOrderRepository.class), sessionTokenService);

        memberId = UUID.randomUUID();
        member = new Member(memberId, "ownerUser", "owner@example.com", "encryptedPw");
        memberRepository.save(member);

        company = new Company(COMPANY_NAME, "desc", memberId);
        companyRepository.save(company);

        when(sessionTokenService.isValid(VALID_TOKEN)).thenReturn(true);
        when(sessionTokenService.extractMemberId(VALID_TOKEN)).thenReturn(memberId);
    }

    @Test
    public void GivenOwner_WhenCreateEvent_ThenSaveAsDraftWithZonesAndVenueMap() {
        appointAs(StaffAppointment.StaffRole.OWNER, Set.of());

        UUID eventId = eventService.createEvent(VALID_TOKEN, validRequest());

        assertNotNull(eventId);
        Event saved = eventRepository.findById(eventId).orElseThrow();
        assertEquals("Spring Concert", saved.getName());
        assertEquals(COMPANY_NAME, saved.getCompanyName());
        assertEquals(EventStatus.DRAFT, saved.getStatus());
        assertEquals(2, saved.getZones().size());
        VenueMap vm = saved.getVenueMap();
        assertNotNull(vm);
        assertEquals(3, vm.getSectionToZone().size());
        assertEquals(
                saved.getZones().stream().map(InventoryZone::getId).collect(java.util.stream.Collectors.toSet()),
                vm.mappedZoneIds());
    }

    @Test
    public void GivenManagerWithBothPermissions_WhenCreateEvent_ThenSucceed() {
        appointAs(StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.MAP_DEFINITION, ManagerPermission.INVENTORY_MGMT));

        UUID eventId = eventService.createEvent(VALID_TOKEN, validRequest());

        assertNotNull(eventId);
        assertTrue(eventRepository.findById(eventId).isPresent());
    }

    @Test
    public void GivenManagerMissingMapDefinition_WhenCreateEvent_ThenThrowSecurityException() {
        appointAs(StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.INVENTORY_MGMT));

        assertThrows(SecurityException.class,
                () -> eventService.createEvent(VALID_TOKEN, validRequest()));
    }

    @Test
    public void GivenManagerMissingInventoryMgmt_WhenCreateEvent_ThenThrowSecurityException() {
        appointAs(StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.MAP_DEFINITION));

        assertThrows(SecurityException.class,
                () -> eventService.createEvent(VALID_TOKEN, validRequest()));
    }

    @Test
    public void GivenManagerWithNoPermissions_WhenCreateEvent_ThenThrowSecurityException() {
        appointAs(StaffAppointment.StaffRole.MANAGER, Set.of());

        assertThrows(SecurityException.class,
                () -> eventService.createEvent(VALID_TOKEN, validRequest()));
    }

    // SUSPENDED/CLOSED tests mock ICompanyRepository — Company has no public
    // status mutator yet (UC-C7 will add it).

    @Test
    public void GivenSuspendedCompany_WhenCreateEvent_ThenThrowIllegalStateException() {
        appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
        useMockedCompanyRepoReturning(stubCompany(COMPANY_NAME, CompanyStatus.SUSPENDED));

        assertThrows(IllegalStateException.class,
                () -> eventService.createEvent(VALID_TOKEN, validRequest()));
    }

    @Test
    public void GivenClosedCompany_WhenCreateEvent_ThenThrowIllegalStateException() {
        appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
        useMockedCompanyRepoReturning(stubCompany(COMPANY_NAME, CompanyStatus.CLOSED));

        assertThrows(IllegalStateException.class,
                () -> eventService.createEvent(VALID_TOKEN, validRequest()));
    }

    @Test
    public void GivenUnknownCompanyName_WhenCreateEvent_ThenThrowIllegalArgumentException() {
        appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
        CreateEventRequest req = new CreateEventRequest(
                "Nonexistent Co", "Spring Concert", "desc", EventCategory.CONCERT,
                defaultSchedule(), defaultLockTimer(),
                defaultZones(), defaultSectionMap());

        assertThrows(IllegalArgumentException.class,
                () -> eventService.createEvent(VALID_TOKEN, req));
    }

    @Test
    public void GivenBlankEventName_WhenBuildRequest_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new CreateEventRequest(
                        COMPANY_NAME, "  ", "desc", EventCategory.CONCERT,
                        defaultSchedule(), defaultLockTimer(),
                        defaultZones(), defaultSectionMap()));
    }

    @Test
    public void GivenNullSchedule_WhenBuildRequest_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new CreateEventRequest(
                        COMPANY_NAME, "Spring Concert", "desc", EventCategory.CONCERT,
                        null, defaultLockTimer(),
                        defaultZones(), defaultSectionMap()));
    }

    @Test
    public void GivenEmptyZones_WhenBuildRequest_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new CreateEventRequest(
                        COMPANY_NAME, "Spring Concert", "desc", EventCategory.CONCERT,
                        defaultSchedule(), defaultLockTimer(),
                        List.of(), defaultSectionMap()));
    }

    @Test
    public void GivenEmptySectionMap_WhenBuildRequest_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new CreateEventRequest(
                        COMPANY_NAME, "Spring Concert", "desc", EventCategory.CONCERT,
                        defaultSchedule(), defaultLockTimer(),
                        defaultZones(), Map.of()));
    }

    @Test
    public void GivenMapReferencesUnknownZone_WhenCreateEvent_ThenThrowIllegalArgumentException() {
        appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
        Map<String, String> badMap = new LinkedHashMap<>();
        badMap.put("Section A", "Floor");
        badMap.put("Section B", "GhostZone");
        CreateEventRequest req = new CreateEventRequest(
                COMPANY_NAME, "Spring Concert", "desc", EventCategory.CONCERT,
                defaultSchedule(), defaultLockTimer(),
                defaultZones(), badMap);

        assertThrows(IllegalArgumentException.class,
                () -> eventService.createEvent(VALID_TOKEN, req));
    }

    @Test
    public void GivenZoneWithoutSection_WhenCreateEvent_ThenThrowIllegalArgumentException() {
        appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
        Map<String, String> partialMap = new LinkedHashMap<>();
        partialMap.put("Section A", "Floor");
        CreateEventRequest req = new CreateEventRequest(
                COMPANY_NAME, "Spring Concert", "desc", EventCategory.CONCERT,
                defaultSchedule(), defaultLockTimer(),
                defaultZones(), partialMap);

        assertThrows(IllegalArgumentException.class,
                () -> eventService.createEvent(VALID_TOKEN, req));
    }

    @Test
    public void GivenGuestToken_WhenCreateEvent_ThenThrowSecurityException() {
        when(sessionTokenService.extractMemberId(VALID_TOKEN)).thenReturn(null);

        assertThrows(SecurityException.class,
                () -> eventService.createEvent(VALID_TOKEN, validRequest()));
    }

    @Test
    public void GivenStaffOfOtherCompany_WhenCreateEvent_ThenThrowSecurityException() {
        StaffAppointment otherCo = new StaffAppointment(
                "Other Co", memberId,
                StaffAppointment.StaffRole.OWNER, Set.of());
        member.addStaffAppointment("Other Co", otherCo);
        memberRepository.save(member);

        assertThrows(SecurityException.class,
                () -> eventService.createEvent(VALID_TOKEN, validRequest()));
    }

    @Test
    public void GivenInvalidToken_WhenCreateEvent_ThenThrowIllegalArgumentException() {
        when(sessionTokenService.isValid(VALID_TOKEN)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> eventService.createEvent(VALID_TOKEN, validRequest()));
    }

    @Test
    public void GivenNullToken_WhenCreateEvent_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.createEvent(null, validRequest()));
    }

    @Test
    public void GivenBlankToken_WhenCreateEvent_ThenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.createEvent("  ", validRequest()));
    }

    // --- helpers ---

    private void appointAs(StaffAppointment.StaffRole role, Set<ManagerPermission> permissions) {
        StaffAppointment appt = new StaffAppointment(
                COMPANY_NAME, memberId, role, permissions);
        member.addStaffAppointment(COMPANY_NAME, appt);
        memberRepository.save(member);
    }

    private void useMockedCompanyRepoReturning(Company stub) {
        ICompanyRepository mocked = mock(ICompanyRepository.class);
        when(mocked.findByName(COMPANY_NAME)).thenReturn(Optional.of(stub));
        eventService = new EventService(
                eventRepository, mocked, memberRepository,
                mock(IOrderRepository.class), sessionTokenService);
    }

    private static Company stubCompany(String name, CompanyStatus status) {
        Company c = mock(Company.class);
        when(c.getName()).thenReturn(name);
        when(c.isActive()).thenReturn(status == CompanyStatus.ACTIVE);
        when(c.getStatus()).thenReturn(status);
        return c;
    }

    private CreateEventRequest validRequest() {
        return new CreateEventRequest(
                COMPANY_NAME,
                "Spring Concert",
                "An outdoor concert",
                EventCategory.CONCERT,
                defaultSchedule(),
                defaultLockTimer(),
                defaultZones(),
                defaultSectionMap());
    }

    private static EventSchedule defaultSchedule() {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        Instant end = start.plus(3, ChronoUnit.HOURS);
        Instant doors = start.minus(1, ChronoUnit.HOURS);
        return new EventSchedule(start, end, doors);
    }

    private static LockTimerDuration defaultLockTimer() {
        return new LockTimerDuration(java.time.Duration.ofMinutes(15));
    }

    private static List<CreateEventRequest.ZoneSpec> defaultZones() {
        return List.of(
                new CreateEventRequest.GAZoneSpec("Floor", new BigDecimal("50.00"), 500),
                new CreateEventRequest.AssignedZoneSpec(
                        "VIP",
                        new BigDecimal("150.00"),
                        List.of(
                                new CreateEventRequest.SeatSpec("A", "1"),
                                new CreateEventRequest.SeatSpec("A", "2"))));
    }

    private static Map<String, String> defaultSectionMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Section A", "Floor");
        m.put("Section B", "Floor");
        m.put("VIP Boxes", "VIP");
        return m;
    }
}
