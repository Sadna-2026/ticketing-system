package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.EventDetailsDTO;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.application.services.EventService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.CompanyStatus;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.event.VenueMap;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;

@DisplayName("EventService")
class EventServiceTest {


    @Nested
    @DisplayName("Create event")
    class CreateEvent {

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
        public void GivenOwnerAndDraftEvent_WhenPublishEvent_ThenEventBecomesPublished() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = eventService.createEvent(VALID_TOKEN, validRequest());

            eventService.publishEvent(VALID_TOKEN, eventId);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertEquals(EventStatus.PUBLISHED, saved.getStatus());
        }

        @Test
        public void GivenManagerWithLifecyclePermission_WhenPublishEvent_ThenEventBecomesPublished() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = eventService.createEvent(VALID_TOKEN, validRequest());
            appointAs(StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.EVENT_LIFECYCLE));

            eventService.publishEvent(VALID_TOKEN, eventId);

            Event saved = eventRepository.findById(eventId).orElseThrow();
            assertEquals(EventStatus.PUBLISHED, saved.getStatus());
        }

        @Test
        public void GivenPublishedEvent_WhenPublishEventAgain_ThenThrowIllegalStateException() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = eventService.createEvent(VALID_TOKEN, validRequest());
            eventService.publishEvent(VALID_TOKEN, eventId);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> eventService.publishEvent(VALID_TOKEN, eventId));

            assertEquals("Can only publish a DRAFT event", ex.getMessage());
        }

        @Test
        public void GivenSuspendedCompany_WhenPublishEvent_ThenThrowOperationNeutralMessage() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = eventService.createEvent(VALID_TOKEN, validRequest());
            useMockedCompanyRepoReturning(stubCompany(COMPANY_NAME, CompanyStatus.SUSPENDED));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> eventService.publishEvent(VALID_TOKEN, eventId));

            assertEquals("Company is suspended or closed: " + COMPANY_NAME, ex.getMessage());
        }

        @Test
        public void GivenManagerWithoutLifecyclePermission_WhenPublishEvent_ThenThrowSecurityException() {
            appointAs(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID eventId = eventService.createEvent(VALID_TOKEN, validRequest());
            appointAs(StaffAppointment.StaffRole.MANAGER,
                    Set.of(ManagerPermission.MAP_DEFINITION, ManagerPermission.INVENTORY_MGMT));

            assertThrows(SecurityException.class,
                    () -> eventService.publishEvent(VALID_TOKEN, eventId));
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
            Company c = new Company(name, "desc", UUID.randomUUID());
            if (status == CompanyStatus.SUSPENDED) {
                c.suspend();
            } else if (status == CompanyStatus.CLOSED) {
                c.close();
            }
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

    

    @Nested
    @DisplayName("Edit event")
    class EditEvent {

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
            when(orderRepo.findActiveByEventId(ArgumentMatchers.any()))
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

    @Nested
    @DisplayName("Inventory")
    class Inventory {

        private static final String COMPANY = "Acme Productions";
        private static final String TOKEN = "valid-token";

        private InMemoryEventRepository eventRepo;
        private InMemoryCompanyRepository companyRepo;
        private InMemoryMemberRepository memberRepo;
        private ISessionTokenService tokens;
        private EventService eventService;

        private UUID memberId;
        private Member member;
        private UUID eventId;
        private UUID gaZoneId;
        private UUID assignedZoneId;

        @BeforeEach
        public void setUp() {
            eventRepo = new InMemoryEventRepository();
            companyRepo = new InMemoryCompanyRepository();
            memberRepo = new InMemoryMemberRepository();
            tokens = mock(ISessionTokenService.class);
            eventService = new EventService(eventRepo, companyRepo, memberRepo,
                    mock(IOrderRepository.class), tokens);

            memberId = UUID.randomUUID();
            member = new Member(memberId, "owner", "owner@x.com", "pw");
            memberRepo.save(member);
            companyRepo.save(new Company(COMPANY, "desc", memberId));

            // seed an event with a GA zone (capacity 100) and an assigned zone (4 seats)
            eventId = UUID.randomUUID();
            Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
            Event e = new Event(eventId, COMPANY, "Concert", "desc", EventCategory.CONCERT,
                    new EventSchedule(start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS)),
                    new LockTimerDuration(Duration.ofMinutes(15)));

            gaZoneId = UUID.randomUUID();
            e.addZone(InventoryZone.createGA(gaZoneId, "Floor", new BigDecimal("50.00"), 100));

            assignedZoneId = UUID.randomUUID();
            InventoryZone vip = InventoryZone.createAssigned(assignedZoneId, "VIP", new BigDecimal("150.00"));
            vip.addSeat(new Seat(UUID.randomUUID(), "A", "1"));
            vip.addSeat(new Seat(UUID.randomUUID(), "A", "2"));
            vip.addSeat(new Seat(UUID.randomUUID(), "A", "3"));
            vip.addSeat(new Seat(UUID.randomUUID(), "A", "4"));
            e.addZone(vip);

            eventRepo.save(e);

            when(tokens.isValid(TOKEN)).thenReturn(true);
            when(tokens.extractMemberId(TOKEN)).thenReturn(memberId);
        }

        // 1. Add ticket to inventory

        @Test
        public void GivenOwner_WhenAddSeatsToAssignedZone_ThenSeatsAdded() {
            appoint(StaffAppointment.StaffRole.OWNER, Set.of());

            eventService.addSeatsToZone(TOKEN, eventId, assignedZoneId, List.of(
                    new CreateEventRequest.SeatSpec("B", "1"),
                    new CreateEventRequest.SeatSpec("B", "2")));

            InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(assignedZoneId);
            assertEquals(6, zone.getSeats().size());
        }

        @Test
        public void GivenOwner_WhenIncreaseGACapacity_ThenCapacityGrows() {
            appoint(StaffAppointment.StaffRole.OWNER, Set.of());

            eventService.increaseGACapacity(TOKEN, eventId, gaZoneId, 50);

            InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(gaZoneId);
            assertEquals(150, zone.getMaxCapacity());
            assertEquals(150, zone.getAvailableCount());
        }

        // 2. Remove ticket — only if not reserved/sold

        @Test
        public void GivenAvailableSeat_WhenRemoveSeats_ThenSeatRemoved() {
            appoint(StaffAppointment.StaffRole.OWNER, Set.of());
            InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(assignedZoneId);
            UUID toRemove = zone.getSeats().get(0).getId();

            eventService.removeSeats(TOKEN, eventId, assignedZoneId, List.of(toRemove));

            InventoryZone after = eventRepo.findById(eventId).orElseThrow().findZone(assignedZoneId);
            assertEquals(3, after.getSeats().size());
        }

        @Test
        public void GivenLockedSeat_WhenRemoveSeats_ThenThrowIllegalStateException() {
            appoint(StaffAppointment.StaffRole.OWNER, Set.of());
            InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(assignedZoneId);
            UUID lockedSeatId = zone.getSeats().get(0).getId();
            zone.lockSeat(lockedSeatId); // simulate active reservation
            eventRepo.save(eventRepo.findById(eventId).orElseThrow());

            assertThrows(IllegalStateException.class,
                    () -> eventService.removeSeats(TOKEN, eventId, assignedZoneId, List.of(lockedSeatId)));
        }

        @Test
        public void GivenInsufficientFreeGA_WhenDecreaseCapacity_ThenThrowIllegalStateException() {
            appoint(StaffAppointment.StaffRole.OWNER, Set.of());
            // lock 90 of the 100 — only 10 are free
            InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(gaZoneId);
            zone.lockGA(90);
            eventRepo.save(eventRepo.findById(eventId).orElseThrow());

            assertThrows(IllegalStateException.class,
                    () -> eventService.decreaseGACapacity(TOKEN, eventId, gaZoneId, 50));
        }

        // 3. Modify ticket price — only if not sold (or locked, V1 strict)

        @Test
        public void GivenZeroSales_WhenSetZonePrice_ThenPriceUpdated() {
            appoint(StaffAppointment.StaffRole.OWNER, Set.of());

            eventService.setZonePrice(TOKEN, eventId, gaZoneId, new BigDecimal("75.00"));

            BigDecimal price = eventRepo.findById(eventId).orElseThrow()
                    .findZone(gaZoneId).getPricePerTicket();
            assertEquals(0, price.compareTo(new BigDecimal("75.00")));
        }

        @Test
        public void GivenLockedTickets_WhenSetZonePrice_ThenThrowIllegalStateException() {
            appoint(StaffAppointment.StaffRole.OWNER, Set.of());
            InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(gaZoneId);
            zone.lockGA(5);
            eventRepo.save(eventRepo.findById(eventId).orElseThrow());

            assertThrows(IllegalStateException.class,
                    () -> eventService.setZonePrice(TOKEN, eventId, gaZoneId, new BigDecimal("80.00")));
        }

        @Test
        public void GivenSoldTickets_WhenSetZonePrice_ThenThrowIllegalStateException() {
            appoint(StaffAppointment.StaffRole.OWNER, Set.of());
            InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(gaZoneId);
            zone.lockGA(3);
            zone.sellGA(3);
            eventRepo.save(eventRepo.findById(eventId).orElseThrow());

            assertThrows(IllegalStateException.class,
                    () -> eventService.setZonePrice(TOKEN, eventId, gaZoneId, new BigDecimal("90.00")));
        }

        // 4. Permission check

        @Test
        public void GivenManagerWithInventoryMgmt_WhenAddSeats_ThenSucceed() {
            appoint(StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.INVENTORY_MGMT));

            eventService.addSeatsToZone(TOKEN, eventId, assignedZoneId,
                    List.of(new CreateEventRequest.SeatSpec("Z", "1")));

            assertEquals(5, eventRepo.findById(eventId).orElseThrow()
                    .findZone(assignedZoneId).getSeats().size());
        }

        @Test
        public void GivenManagerWithMapDefinition_WhenIncreaseCapacity_ThenSucceed() {
            appoint(StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.MAP_DEFINITION));

            eventService.increaseGACapacity(TOKEN, eventId, gaZoneId, 10);

            assertEquals(110, eventRepo.findById(eventId).orElseThrow()
                    .findZone(gaZoneId).getMaxCapacity());
        }

        @Test
        public void GivenManagerWithoutEitherPermission_WhenSetPrice_ThenThrowSecurityException() {
            appoint(StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.EVENT_LIFECYCLE));

            assertThrows(SecurityException.class,
                    () -> eventService.setZonePrice(TOKEN, eventId, gaZoneId, new BigDecimal("99.00")));
        }

        @Test
        public void GivenGuestToken_WhenAddSeats_ThenThrowSecurityException() {
            when(tokens.extractMemberId(TOKEN)).thenReturn(null);

            assertThrows(SecurityException.class,
                    () -> eventService.addSeatsToZone(TOKEN, eventId, assignedZoneId,
                            List.of(new CreateEventRequest.SeatSpec("X", "1"))));
        }

        // 5. Active locked-order conflict — covered by GivenLockedSeat_WhenRemoveSeats above

        // 6. Concurrent edit safety — race test per V1 §6.a

        @Test
        public void GivenConcurrentSeatAdds_WhenRunInParallel_ThenAllAddsApplied() throws Exception {
            appoint(StaffAppointment.StaffRole.OWNER, Set.of());

            final int threadCount = 8;
            final int seatsPerThread = 5;
            final ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            final CountDownLatch start = new CountDownLatch(1);
            final CountDownLatch done = new CountDownLatch(threadCount);
            final AtomicInteger failures = new AtomicInteger();

            for (int t = 0; t < threadCount; t++) {
                final int tid = t;
                pool.submit(() -> {
                    try {
                        start.await(); // all threads block until released
                        List<CreateEventRequest.SeatSpec> batch = new java.util.ArrayList<>();
                        for (int i = 0; i < seatsPerThread; i++) {
                            batch.add(new CreateEventRequest.SeatSpec("T" + tid, String.valueOf(i)));
                        }
                        eventService.addSeatsToZone(TOKEN, eventId, assignedZoneId, batch);
                    } catch (Exception ex) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown(); // unleash
            assertTrue(done.await(10, TimeUnit.SECONDS), "concurrent adds did not finish in time");
            pool.shutdown();

            assertEquals(0, failures.get(), "no concurrent add should fail");
            // started with 4 seats; added 8 * 5 = 40 → expect 44
            InventoryZone zone = eventRepo.findById(eventId).orElseThrow().findZone(assignedZoneId);
            assertEquals(4 + threadCount * seatsPerThread, zone.getSeats().size());
        }

        @Test
        public void GivenConcurrentSameSeatRemoval_WhenRunInParallel_ThenExactlyOneSucceeds() throws Exception {
            appoint(StaffAppointment.StaffRole.OWNER, Set.of());
            UUID seatId = eventRepo.findById(eventId).orElseThrow()
                    .findZone(assignedZoneId).getSeats().get(0).getId();

            final int threadCount = 6;
            final ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            final CountDownLatch start = new CountDownLatch(1);
            final CountDownLatch done = new CountDownLatch(threadCount);
            final AtomicInteger successes = new AtomicInteger();
            final AtomicInteger expectedFailures = new AtomicInteger();

            for (int t = 0; t < threadCount; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        eventService.removeSeats(TOKEN, eventId, assignedZoneId, List.of(seatId));
                        successes.incrementAndGet();
                    } catch (IllegalArgumentException | IllegalStateException ex) {
                        expectedFailures.incrementAndGet(); // seat-not-found after first removal
                    } catch (Exception ignore) {
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
            pool.shutdown();

            assertEquals(1, successes.get(), "exactly one removal should succeed");
            assertEquals(threadCount - 1, expectedFailures.get(),
                    "all other threads should see the seat already gone");
            // and the seat is actually gone
            assertEquals(3, eventRepo.findById(eventId).orElseThrow()
                    .findZone(assignedZoneId).getSeats().size());
        }

        @Test
        public void GivenCancelledEvent_WhenAddSeats_ThenThrowIllegalStateException() {
            appoint(StaffAppointment.StaffRole.OWNER, Set.of());
            Event e = eventRepo.findById(eventId).orElseThrow();
            e.cancel();
            eventRepo.save(e);

            assertThrows(IllegalStateException.class,
                    () -> eventService.addSeatsToZone(TOKEN, eventId, assignedZoneId,
                            List.of(new CreateEventRequest.SeatSpec("X", "1"))));
        }

        @Test
        public void GivenTwoDifferentEvents_WhenEditedInParallel_ThenBothComplete() throws Exception {
            appoint(StaffAppointment.StaffRole.OWNER, Set.of());

            // seed a second event under the same company
            UUID otherEventId = UUID.randomUUID();
            UUID otherZoneId = UUID.randomUUID();
            Instant start = Instant.now().plus(45, ChronoUnit.DAYS);
            Event other = new Event(otherEventId, COMPANY, "Concert 2", "desc", EventCategory.CONCERT,
                    new EventSchedule(start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS)),
                    new LockTimerDuration(Duration.ofMinutes(15)));
            InventoryZone vip2 = InventoryZone.createAssigned(otherZoneId, "VIP", new BigDecimal("100.00"));
            other.addZone(vip2);
            eventRepo.save(other);

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start_ = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);

            pool.submit(() -> {
                try { start_.await();
                    eventService.addSeatsToZone(TOKEN, eventId, assignedZoneId,
                            List.of(new CreateEventRequest.SeatSpec("E1", "1")));
                } catch (Exception ignore) {} finally { done.countDown(); }
            });
            pool.submit(() -> {
                try { start_.await();
                    eventService.addSeatsToZone(TOKEN, otherEventId, otherZoneId,
                            List.of(new CreateEventRequest.SeatSpec("E2", "1")));
                } catch (Exception ignore) {} finally { done.countDown(); }
            });

            start_.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            pool.shutdown();

            assertEquals(5, eventRepo.findById(eventId).orElseThrow()
                    .findZone(assignedZoneId).getSeats().size());
            assertEquals(1, eventRepo.findById(otherEventId).orElseThrow()
                    .findZone(otherZoneId).getSeats().size());
        }

        // helpers

        private void appoint(StaffAppointment.StaffRole role, Set<ManagerPermission> perms) {
            member.addStaffAppointment(COMPANY,
                    new StaffAppointment(COMPANY, memberId, role, perms));
            memberRepo.save(member);
        }
    }

    @Nested
    @DisplayName("Cancel event")
    class CancelEvent {

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
                    mock(IOrderRepository.class), tokens);

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



    // read event add the tests here
    @Nested
    @DisplayName("Read event")
    class ReadEvent {

        @Nested
        @DisplayName("Query")
        class Query {

            private static final String COMPANY = "Acme Productions";

            private InMemoryEventRepository eventRepo;
            private EventService service;

            @BeforeEach
            public void setUp() {
                eventRepo = new InMemoryEventRepository();
                service = new EventService(eventRepo, new InMemoryCompanyRepository(), new InMemoryMemberRepository(),
                        mock(IOrderRepository.class), mock(ISessionTokenService.class));
            }

            @Test
            public void GivenPublishedEvent_WhenGetEventMap_ThenReturnsMapAndInventory() {
                UUID eventId = UUID.randomUUID();
                Event e = newEvent(eventId, "Spring Concert");
                UUID gaZoneId = addGAZone(e, "Floor", new BigDecimal("50.00"), 100);
                UUID vipZoneId = addAssignedZone(e, "VIP", new BigDecimal("150.00"), 4);
                attachVenueMap(e, Map.of("Section A", gaZoneId, "VIP Boxes", vipZoneId));
                e.publish();
                eventRepo.save(e);

                EventMapDTO dto = service.getEventMap(eventId).orElseThrow();

                assertEquals(eventId, dto.eventId());
                assertEquals("Spring Concert", dto.eventName());
                assertEquals(COMPANY, dto.companyName());
                assertEquals(EventStatus.PUBLISHED, dto.status());
                assertEquals(2, dto.venueMap().size());
                assertEquals(2, dto.zones().size());
            }

            @Test
            public void GivenLiveZoneState_WhenGetEventMap_ThenAvailabilityCountsReflectIt() {
                UUID eventId = UUID.randomUUID();
                Event e = newEvent(eventId, "Live");
                UUID gaZoneId = addGAZone(e, "Floor", new BigDecimal("40.00"), 100);
                UUID vipZoneId = addAssignedZone(e, "VIP", new BigDecimal("200.00"), 3);
                attachVenueMap(e, Map.of("S", gaZoneId, "V", vipZoneId));
                e.publish();

                InventoryZone ga = e.findZone(gaZoneId);
                ga.lockGA(20);
                ga.sellGA(5);

                InventoryZone vip = e.findZone(vipZoneId);
                UUID firstSeat = vip.getSeats().get(0).getId();
                vip.lockSeat(firstSeat);

                eventRepo.save(e);

                EventMapDTO dto = service.getEventMap(eventId).orElseThrow();

                EventMapDTO.ZoneInfo gaInfo = findZone(dto, gaZoneId);
                assertEquals(80, gaInfo.availableCount());
                assertEquals(5, gaInfo.soldCount());

                EventMapDTO.ZoneInfo vipInfo = findZone(dto, vipZoneId);
                assertEquals(3, vipInfo.seats().size());

                long unavailable = vipInfo.seats().stream()
                        .filter(s -> !s.available())
                        .count();

                assertEquals(1, unavailable, "the locked seat shows as unavailable");
            }

            @Test
            public void GivenUnknownEventId_WhenGetEventMap_ThenReturnsEmpty() {
                assertTrue(service.getEventMap(UUID.randomUUID()).isEmpty());
            }

            @Test
            public void GivenNullEventId_WhenGetEventMap_ThenReturnsEmpty() {
                assertTrue(service.getEventMap(null).isEmpty());
            }

            @Test
            public void GivenCancelledEvent_WhenGetEventMap_ThenReturnsEmpty() {
                UUID eventId = UUID.randomUUID();
                Event e = newEvent(eventId, "Cancelled Concert");
                addGAZone(e, "Floor", new BigDecimal("40.00"), 50);
                e.publish();
                e.cancel();
                eventRepo.save(e);

                assertTrue(service.getEventMap(eventId).isEmpty(),
                        "cancelled events are not browsable for reservation");
            }

            @Test
            public void GivenDraftEvent_WhenGetEventMap_ThenReturnsEmpty() {
                UUID eventId = UUID.randomUUID();
                Event e = newEvent(eventId, "Draft");
                addGAZone(e, "Floor", new BigDecimal("10.00"), 10);
                eventRepo.save(e);

                assertTrue(service.getEventMap(eventId).isEmpty());
            }

            @Test
            public void GivenSoldOutEvent_WhenGetEventMap_ThenReturnsDtoWithSoldOutStatus() {
                UUID eventId = UUID.randomUUID();
                Event e = newEvent(eventId, "Hot Show");
                UUID gaZoneId = addGAZone(e, "Floor", new BigDecimal("30.00"), 5);
                attachVenueMap(e, Map.of("S", gaZoneId));
                e.publish();

                InventoryZone ga = e.findZone(gaZoneId);
                ga.lockGA(5);
                ga.sellGA(5);

                e.markSoldOut();
                eventRepo.save(e);

                EventMapDTO dto = service.getEventMap(eventId).orElseThrow();

                assertEquals(EventStatus.SOLD_OUT, dto.status());
            }

            @Test
            public void GivenPublishedEventWithoutVenueMap_WhenGetEventMap_ThenReturnsEmpty() {
                UUID eventId = UUID.randomUUID();
                Event e = newEvent(eventId, "Misconfigured");
                addGAZone(e, "Floor", new BigDecimal("10.00"), 10);
                e.publish();
                eventRepo.save(e);

                assertTrue(service.getEventMap(eventId).isEmpty(),
                        "events without a venue map cannot render and shouldn't be browsable");
            }

            @Test
            public void GivenDtoReturned_WhenSourceMutated_ThenDtoUnchanged() {
                UUID eventId = UUID.randomUUID();
                Event e = newEvent(eventId, "Live");
                UUID gaZoneId = addGAZone(e, "Floor", new BigDecimal("10.00"), 10);
                attachVenueMap(e, Map.of("S", gaZoneId));
                e.publish();
                eventRepo.save(e);

                EventMapDTO dto = service.getEventMap(eventId).orElseThrow();
                int before = dto.zones().size();

                e.findZone(dto.zones().get(0).id()).lockGA(3);

                assertEquals(before, dto.zones().size());
            }

            @Test
            public void GivenDtoReturned_WhenCallerMutatesZonesList_ThenThrows() {
                UUID eventId = UUID.randomUUID();
                Event e = newEvent(eventId, "Live");
                UUID gaZoneId = addGAZone(e, "Floor", new BigDecimal("10.00"), 10);
                attachVenueMap(e, Map.of("S", gaZoneId));
                e.publish();
                eventRepo.save(e);

                EventMapDTO dto = service.getEventMap(eventId).orElseThrow();

                assertThrows(UnsupportedOperationException.class, () -> dto.zones().clear());
            }

            private static Event newEvent(UUID id, String name) {
                Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
                return new Event(
                        id,
                        COMPANY,
                        name,
                        "desc",
                        EventCategory.CONCERT,
                        new EventSchedule(
                                start,
                                start.plus(2, ChronoUnit.HOURS),
                                start.minus(1, ChronoUnit.HOURS)),
                        new LockTimerDuration(Duration.ofMinutes(15)));
            }

            private static UUID addGAZone(Event e, String name, BigDecimal price, int capacity) {
                UUID id = UUID.randomUUID();
                e.addZone(InventoryZone.createGA(id, name, price, capacity));
                return id;
            }

            private static UUID addAssignedZone(Event e, String name, BigDecimal price, int seatCount) {
                UUID id = UUID.randomUUID();
                InventoryZone z = InventoryZone.createAssigned(id, name, price);

                for (int i = 1; i <= seatCount; i++) {
                    z.addSeat(new Seat(UUID.randomUUID(), "A", String.valueOf(i)));
                }

                e.addZone(z);
                return id;
            }

            private static void attachVenueMap(Event e, Map<String, UUID> sectionToZoneId) {
                Map<String, UUID> copy = new LinkedHashMap<>(sectionToZoneId);
                e.setVenueMap(new VenueMap(copy));
            }

            private static EventMapDTO.ZoneInfo findZone(EventMapDTO dto, UUID zoneId) {
                EventMapDTO.ZoneInfo found = dto.zones().stream()
                        .filter(z -> z.id().equals(zoneId))
                        .findFirst()
                        .orElse(null);

                assertNotNull(found, "zone not in DTO: " + zoneId);
                return found;
            }
        }

        @Nested
        @DisplayName("Search")
        class Search {

            private static final String COMPANY_A = "Acme Productions";
            private static final String COMPANY_B = "Beta Music";

            private InMemoryEventRepository eventRepo;
            private InMemoryCompanyRepository companyRepo;
            private EventService service;

            @BeforeEach
            public void setUp() {
                eventRepo = new InMemoryEventRepository();
                companyRepo = new InMemoryCompanyRepository();
                service = new EventService(eventRepo, companyRepo, new InMemoryMemberRepository(),
                        mock(IOrderRepository.class), mock(ISessionTokenService.class));

                companyRepo.save(new Company(COMPANY_A, "x", UUID.randomUUID()));
                companyRepo.save(new Company(COMPANY_B, "x", UUID.randomUUID()));
            }

            @Test
            public void GivenMatchingNameAndPublishedEvent_WhenSearch_ThenReturnsHit() {
                publish(COMPANY_A, "Spring Concert", "Beethoven", EventCategory.CONCERT,
                        "Tel Aviv", new BigDecimal("50.00"), 30);

                List<EventSummaryDTO> hits = service.searchEvents(
                        new SearchEventsRequest("spring", null, null, null, null, null, null, null));

                assertEquals(1, hits.size());
                assertEquals("Spring Concert", hits.get(0).name());
            }

            @Test
            public void GivenNoFilters_WhenSearch_ThenReturnsAllVisibleEvents() {
                publish(COMPANY_A, "Concert 1", "X", EventCategory.CONCERT,
                        "TLV", new BigDecimal("10"), 30);
                publish(COMPANY_B, "Concert 2", "Y", EventCategory.FESTIVAL,
                        "JLM", new BigDecimal("20"), 60);

                List<EventSummaryDTO> hits = service.searchEvents(SearchEventsRequest.empty());

                assertEquals(2, hits.size());
            }

            @Test
            public void GivenNoMatch_WhenSearch_ThenReturnsEmptyListNotError() {
                publish(COMPANY_A, "Spring Concert", "Beethoven", EventCategory.CONCERT,
                        "TLV", new BigDecimal("50"), 30);

                List<EventSummaryDTO> hits = service.searchEvents(
                        new SearchEventsRequest("nonexistent", null, null, null, null, null, null, null));

                assertTrue(hits.isEmpty(), "no error, just empty list");
            }

            @Test
            public void GivenMultipleFilters_WhenSearch_ThenAndSemanticsApply() {
                publish(COMPANY_A, "Jazz Night", "Miles", EventCategory.CONCERT,
                        "TLV", new BigDecimal("80"), 30);
                publish(COMPANY_A, "Jazz Brunch", "Miles", EventCategory.FESTIVAL,
                        "TLV", new BigDecimal("80"), 30);
                publish(COMPANY_A, "Jazz Night", "Miles", EventCategory.CONCERT,
                        "JLM", new BigDecimal("80"), 30);

                SearchEventsRequest req = new SearchEventsRequest(
                        "jazz", "TLV", EventCategory.CONCERT, null, null, null, null, null);

                List<EventSummaryDTO> hits = service.searchEvents(req);

                assertEquals(1, hits.size());
                assertEquals("Jazz Night", hits.get(0).name());
                assertEquals(EventCategory.CONCERT, hits.get(0).category());
            }

            @Test
            public void GivenCancelledEvent_WhenSearch_ThenExcluded() {
                UUID id = UUID.randomUUID();
                Event e = newEvent(id, COMPANY_A, "Cancelled Concert", "X",
                        EventCategory.CONCERT, "TLV", new BigDecimal("50"), 30);
                e.publish();
                e.cancel();
                eventRepo.save(e);

                List<EventSummaryDTO> hits = service.searchEvents(SearchEventsRequest.empty());

                assertTrue(hits.isEmpty());
            }

            @Test
            public void GivenDraftEvent_WhenSearch_ThenExcluded() {
                UUID id = UUID.randomUUID();
                Event e = newEvent(id, COMPANY_A, "Draft Event", "X",
                        EventCategory.CONCERT, "TLV", new BigDecimal("50"), 30);

                eventRepo.save(e);

                assertTrue(service.searchEvents(SearchEventsRequest.empty()).isEmpty());
            }

            @Test
            public void GivenEventOfSuspendedCompany_WhenSearch_ThenExcluded() {
                publish(COMPANY_A, "Hidden", "X", EventCategory.CONCERT,
                        "TLV", new BigDecimal("50"), 30);

                Company a = companyRepo.findByName(COMPANY_A).orElseThrow();
                a.suspend();
                companyRepo.save(a);

                assertTrue(service.searchEvents(SearchEventsRequest.empty()).isEmpty());
            }

            @Test
            public void GivenEventOfClosedCompany_WhenSearch_ThenExcluded() {
                publish(COMPANY_A, "Hidden", "X", EventCategory.CONCERT,
                        "TLV", new BigDecimal("50"), 30);

                Company a = companyRepo.findByName(COMPANY_A).orElseThrow();
                a.close();
                companyRepo.save(a);

                assertTrue(service.searchEvents(SearchEventsRequest.empty()).isEmpty());
            }

            @Test
            public void GivenCompanyNameFilter_WhenSearch_ThenOnlyThatCompanysEvents() {
                publish(COMPANY_A, "Show A", "X", EventCategory.CONCERT,
                        "TLV", new BigDecimal("50"), 30);
                publish(COMPANY_B, "Show B", "X", EventCategory.CONCERT,
                        "TLV", new BigDecimal("50"), 30);

                List<EventSummaryDTO> hits = service.searchEvents(
                        new SearchEventsRequest(null, null, null, COMPANY_A, null, null, null, null));

                assertEquals(1, hits.size());
                assertEquals("Show A", hits.get(0).name());
            }

            @Test
            public void GivenPriceRange_WhenSearch_ThenOnlyEventsWithMatchingPriceZone() {
                publish(COMPANY_A, "Cheap", "X", EventCategory.CONCERT,
                        "TLV", new BigDecimal("20"), 30);
                publish(COMPANY_A, "Pricey", "X", EventCategory.CONCERT,
                        "TLV", new BigDecimal("200"), 30);

                List<EventSummaryDTO> noHits = service.searchEvents(
                        new SearchEventsRequest(null, null, null, null,
                                new BigDecimal("50"), new BigDecimal("100"), null, null));

                assertTrue(noHits.isEmpty());

                List<EventSummaryDTO> cheap = service.searchEvents(
                        new SearchEventsRequest(null, null, null, null,
                                null, new BigDecimal("50"), null, null));

                assertEquals(1, cheap.size());
                assertEquals("Cheap", cheap.get(0).name());
            }

            @Test
            public void GivenDateRange_WhenSearch_ThenOnlyEventsInRange() {
                publish(COMPANY_A, "Soon", "X", EventCategory.CONCERT,
                        "TLV", new BigDecimal("50"), 10);
                publish(COMPANY_A, "Later", "X", EventCategory.CONCERT,
                        "TLV", new BigDecimal("50"), 90);

                Instant from = Instant.now().plus(30, ChronoUnit.DAYS);
                Instant to = Instant.now().plus(120, ChronoUnit.DAYS);

                List<EventSummaryDTO> hits = service.searchEvents(
                        new SearchEventsRequest(null, null, null, null, null, null, from, to));

                assertEquals(1, hits.size());
                assertEquals("Later", hits.get(0).name());
            }

            @Test
            public void GivenNullRequest_WhenSearch_ThenTreatedAsEmptyFilters() {
                publish(COMPANY_A, "Show", "X", EventCategory.CONCERT,
                        "TLV", new BigDecimal("50"), 30);

                List<EventSummaryDTO> hits = service.searchEvents(null);

                assertEquals(1, hits.size());
            }

            @Test
            public void GivenRegionFilterWithDifferentCase_WhenSearch_ThenStillMatches() {
                publish(COMPANY_A, "Show", "X", EventCategory.CONCERT,
                        "Tel Aviv", new BigDecimal("50"), 30);

                List<EventSummaryDTO> hits = service.searchEvents(
                        new SearchEventsRequest(null, "tel aviv", null, null, null, null, null, null));

                assertEquals(1, hits.size());
            }

            @Test
            public void GivenInvertedPriceRange_WhenBuildRequest_ThenThrowIllegalArgumentException() {
                assertThrows(IllegalArgumentException.class,
                        () -> new SearchEventsRequest(null, null, null, null,
                                new BigDecimal("100"), new BigDecimal("50"), null, null));
            }

            @Test
            public void GivenInvertedDateRange_WhenBuildRequest_ThenThrowIllegalArgumentException() {
                Instant later = Instant.now().plus(60, ChronoUnit.DAYS);
                Instant earlier = Instant.now().plus(10, ChronoUnit.DAYS);

                assertThrows(IllegalArgumentException.class,
                        () -> new SearchEventsRequest(null, null, null, null,
                                null, null, later, earlier));
            }

            private void publish(String companyName, String name, String artist,
                                 EventCategory category, String region,
                                 BigDecimal price, int daysFromNow) {
                UUID id = UUID.randomUUID();
                Event e = newEvent(id, companyName, name, artist, category, region, price, daysFromNow);
                e.publish();
                eventRepo.save(e);
            }

            private static Event newEvent(UUID id, String companyName, String name,
                                          String artist, EventCategory category,
                                          String region, BigDecimal price,
                                          int daysFromNow) {
                Instant start = Instant.now().plus(daysFromNow, ChronoUnit.DAYS);

                EventSchedule s = new EventSchedule(
                        start,
                        start.plus(2, ChronoUnit.HOURS),
                        start.minus(1, ChronoUnit.HOURS));

                Event e = new Event(
                        id,
                        companyName,
                        name,
                        "desc",
                        category,
                        s,
                        new LockTimerDuration(Duration.ofMinutes(15)));

                e.setArtist(artist);
                e.setRegion(region);
                e.addZone(InventoryZone.createGA(UUID.randomUUID(), "Floor", price, 50));

                return e;
            }
        }
    }
}
