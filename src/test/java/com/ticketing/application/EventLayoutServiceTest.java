package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.services.EventService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LayoutCell;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.SaleMethod;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.event.VenueLayout;
import com.ticketing.domain.event.VenueMap;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryLotteryRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;

@DisplayName("EventService visual hall layout (FIX-V2-25)")
class EventLayoutServiceTest {

    private static final String COMPANY = "Hall Corp";
    private static final String OWNER_TOKEN = "owner-token";
    private static final Instant NOW = Instant.parse("2026-07-01T12:00:00Z");

    private InMemoryEventRepository eventRepository;
    private InMemoryCompanyRepository companyRepository;
    private InMemoryMemberRepository memberRepository;
    private ISessionTokenService sessionTokenService;
    private EventService eventService;

    private UUID ownerId;
    private UUID eventId;
    private UUID assignedZoneId;
    private UUID gaZoneId;
    private UUID seatId;

    @BeforeEach
    void setUp() {
        eventRepository = new InMemoryEventRepository();
        companyRepository = new InMemoryCompanyRepository();
        memberRepository = new InMemoryMemberRepository();
        sessionTokenService = mock(ISessionTokenService.class);
        eventService = new EventService(eventRepository, companyRepository, memberRepository,
                mock(IOrderRepository.class), sessionTokenService, new InMemoryLotteryRepository(), () -> NOW);

        ownerId = UUID.randomUUID();
        Member owner = new Member(ownerId, "owner", "owner@test.com", "pw");
        owner.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, null, StaffAppointment.StaffRole.OWNER, Set.of()));
        memberRepository.save(owner);
        companyRepository.save(new Company(COMPANY, "desc", ownerId));
        when(sessionTokenService.isValid(OWNER_TOKEN)).thenReturn(true);
        when(sessionTokenService.extractMemberId(OWNER_TOKEN)).thenReturn(ownerId);

        eventId = UUID.randomUUID();
        assignedZoneId = UUID.randomUUID();
        gaZoneId = UUID.randomUUID();
        seatId = UUID.randomUUID();
        Event event = new Event(eventId, COMPANY, "Big Show", "desc", EventCategory.CONCERT,
                schedule(), new LockTimerDuration(Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(), SaleMethod.REGULAR, null);
        InventoryZone assigned = InventoryZone.createAssigned(assignedZoneId, "VIP", new BigDecimal("150.00"));
        assigned.addSeat(new Seat(seatId, "A", "1"));
        event.addZone(assigned);
        event.addZone(InventoryZone.createGA(gaZoneId, "Floor", new BigDecimal("50.00"), 100));
        event.setVenueMap(new VenueMap(Map.of("VIP", assignedZoneId, "Floor", gaZoneId)));
        eventRepository.save(event); // stays DRAFT
    }

    private VenueLayout validLayout() {
        return new VenueLayout(2, 2, List.of(
                LayoutCell.seat(0, 0, assignedZoneId, seatId),
                LayoutCell.ga(0, 1, gaZoneId, "Floor"),
                LayoutCell.stage(1, 0, "Main Stage"),
                LayoutCell.blocked(1, 1)));
    }

    private String tokenFor(UUID memberId, String token) {
        when(sessionTokenService.isValid(token)).thenReturn(true);
        when(sessionTokenService.extractMemberId(token)).thenReturn(memberId);
        return token;
    }

    @Test
    void GivenOwner_WhenSetEventLayout_ThenLayoutPersistedOnDraftEvent() {
        eventService.setEventLayout(OWNER_TOKEN, eventId, validLayout());

        VenueLayout saved = eventRepository.findById(eventId).orElseThrow().getVenueLayout();
        assertNotNull(saved);
        assertEquals(4, saved.getCells().size());
        assertEquals(2, saved.getRows());
    }

    @Test
    void GivenLayoutReferencesUnknownSeat_WhenSetEventLayout_ThenThrows() {
        VenueLayout bad = new VenueLayout(1, 1, List.of(LayoutCell.seat(0, 0, assignedZoneId, UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class, () -> eventService.setEventLayout(OWNER_TOKEN, eventId, bad));
    }

    @Test
    void GivenLayoutReferencesUnknownZone_WhenSetEventLayout_ThenThrows() {
        VenueLayout bad = new VenueLayout(1, 1, List.of(LayoutCell.ga(0, 0, UUID.randomUUID(), "Ghost")));
        assertThrows(IllegalArgumentException.class, () -> eventService.setEventLayout(OWNER_TOKEN, eventId, bad));
    }

    @Test
    void GivenNonStaffMember_WhenSetEventLayout_ThenSecurityException() {
        UUID strangerId = UUID.randomUUID();
        memberRepository.save(new Member(strangerId, "stranger", "stranger@test.com", "pw"));
        String token = tokenFor(strangerId, "stranger-token");
        assertThrows(SecurityException.class, () -> eventService.setEventLayout(token, eventId, validLayout()));
    }

    @Test
    void GivenManagerWithoutMapDefinition_WhenSetEventLayout_ThenSecurityException() {
        UUID mgrId = UUID.randomUUID();
        Member mgr = new Member(mgrId, "mgr", "mgr@test.com", "pw");
        mgr.addStaffAppointment(COMPANY,
                new StaffAppointment(COMPANY, ownerId, StaffAppointment.StaffRole.MANAGER, Set.of()));
        memberRepository.save(mgr);
        String token = tokenFor(mgrId, "mgr-token");
        assertThrows(SecurityException.class, () -> eventService.setEventLayout(token, eventId, validLayout()));
    }

    @Test
    void GivenManagerWithMapDefinition_WhenSetEventLayout_ThenSucceeds() {
        UUID mgrId = UUID.randomUUID();
        Member mgr = new Member(mgrId, "mgr2", "mgr2@test.com", "pw");
        mgr.addStaffAppointment(COMPANY, new StaffAppointment(COMPANY, ownerId,
                StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.MAP_DEFINITION)));
        memberRepository.save(mgr);
        String token = tokenFor(mgrId, "mgr2-token");

        eventService.setEventLayout(token, eventId, validLayout());
        assertNotNull(eventRepository.findById(eventId).orElseThrow().getVenueLayout());
    }

    @Test
    void GivenPublishedEvent_WhenSetEventLayout_ThenThrowsIllegalState() {
        Event e = eventRepository.findById(eventId).orElseThrow();
        e.publish();
        eventRepository.save(e);
        assertThrows(IllegalStateException.class, () -> eventService.setEventLayout(OWNER_TOKEN, eventId, validLayout()));
    }

    @Test
    void GivenNoLayout_WhenValidateEventLayout_ThenThrowsIllegalState() {
        assertThrows(IllegalStateException.class, () -> eventService.validateEventLayout(OWNER_TOKEN, eventId));
    }

    @Test
    void GivenValidLayoutSaved_WhenValidateEventLayout_ThenNoException() {
        eventService.setEventLayout(OWNER_TOKEN, eventId, validLayout());
        assertDoesNotThrow(() -> eventService.validateEventLayout(OWNER_TOKEN, eventId));
    }

    @Test
    void GivenPublishedEventWithLayout_WhenGetEventMap_ThenLayoutIncluded() {
        eventService.setEventLayout(OWNER_TOKEN, eventId, validLayout());
        Event e = eventRepository.findById(eventId).orElseThrow();
        e.publish();
        eventRepository.save(e);

        EventMapDTO map = eventService.getEventMap(eventId).orElseThrow();
        assertNotNull(map.layout());
        assertEquals(2, map.layout().rows());
        assertEquals(2, map.layout().cols());
        assertEquals(4, map.layout().cells().size());
    }

    private static EventSchedule schedule() {
        Instant start = NOW.plus(30, ChronoUnit.DAYS);
        return new EventSchedule(start, start.plus(3, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS));
    }
}
